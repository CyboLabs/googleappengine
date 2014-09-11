// Copyright 2009 Google Inc. All rights reserved.

package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.GenericApplication.ErrorHandler;
import com.google.appengine.tools.util.FileIterator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Uploads a new appversion to the hosting service.
 *
 */
public class AppVersionUpload {
  /**
   * Don't try to precompile more than this number of files in one request.
   */
  private static final int MAX_FILES_PER_PRECOMPILE = 50;

  private static final String YAML_EMPTY_STRING = "null";

  private static final String PRECOMPILATION_FAILED_WARNING_MESSAGE =
      "Precompilation failed.  Consider retrying the update later, or add"
      + " <precompilation-enabled>false</precompilation-enabled> to your appengine-web.xml"
      + " to disable precompilation.";

  private static final Logger logger = Logger.getLogger(AppVersionUpload.class.getName());

  /**
   * Status codes that can be returned by the /api/isconfigupdated endpoint.  These indicate whether
   * the app's Google Cloud Endpoints API configuration has been updated (if there is one).
   */
  @VisibleForTesting
  enum EndpointsServingStatus {
    SERVING("serving"),
    PENDING("pending"),
    FAILED("failed");

    private final String parseName;

    EndpointsServingStatus(String parseName) {
      this.parseName = parseName;
    }

    static EndpointsServingStatus parse(String value) {
      for (EndpointsServingStatus status : EndpointsServingStatus.values()) {
        if (value.equalsIgnoreCase(status.parseName)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Value is not a recognized EndpointsServingStatus:"
          + value);
    }
  }

  protected ServerConnection connection;
  protected GenericApplication app;
  protected final String backend;
  private boolean inTransaction = false;
  private Map<String, FileInfo> files = new HashMap<String, FileInfo>();
  private boolean deployed = false;
  private boolean started = false;
  private boolean checkConfigUpdated = false;
  private final UploadBatcher fileBatcher;
  private final UploadBatcher blobBatcher;
  private ClientDeploySender clientDeploySender;
  private SleepIfShouldRetry sleepIfShouldRetry;

  public AppVersionUpload(ServerConnection connection, GenericApplication app) {
    this(connection, app, null, true);
  }

  /**
   * Create a new {@link AppVersionUpload} instance that can deploy a new
   * versions of {@code app} via {@code connection}.
   *
   * @param connection to connect to the server
   * @param app that contains the code to be deployed
   * @param backend if supplied and non-{@code null}, a particular backend is
   *        being updated
   */
  public AppVersionUpload(ServerConnection connection, GenericApplication app,
      String backend, boolean batchMode) {
    this.connection = connection;
    this.app = app;
    this.backend = backend;
    this.clientDeploySender = new NoLoggingClientDeploySender(connection);
    fileBatcher = new UploadBatcher("file", batchMode);
    blobBatcher = new UploadBatcher("blob", batchMode);
    sleepIfShouldRetry = new DefaultSleepAndRetry();
  }

  /**
   * Get an AppVersionUpload that already has the started flag set to true.
   */
  @VisibleForTesting
  static AppVersionUpload getStartedAppForTesting(ServerConnection connection,
      GenericApplication app) {
    AppVersionUpload upload = new AppVersionUpload(connection, app);
    upload.started = true;
    return upload;
  }

  /***
   * Uploads a new appversion to the server.
   *
   * @throws LocalIOException if a problem occurs with local files.
   * @throws RemoteIOException if a problem occurs communicating with the server.
   */
  public void doUpload(ResourceLimits resourceLimits, boolean updateGlobalConfigurations,
      boolean failOnPrecompilationError, ClientDeploySender clientDeploySender)
      throws LocalIOException, RemoteIOException {

    ClientDeploySender originalClientDeploySender = this.clientDeploySender;
    this.clientDeploySender = Preconditions.checkNotNull(clientDeploySender);
    try {
      uploadFilesTransaction(resourceLimits, failOnPrecompilationError);
    } finally {
      clientDeploySender = originalClientDeploySender;
    }
    if (updateGlobalConfigurations) {
      updateIndexes();
      updateCron();
      updateQueue();
      updateDos();
      updatePagespeed();
      reportIfSkippingDispatchConfiguration();
    } else {
      reportSkippingGlobalConfiguration();
    }
  }

  private void uploadFilesTransaction(
      ResourceLimits resourceLimits,
      boolean failOnPrecompilationError) throws LocalIOException, RemoteIOException {
    try {
      try {
        File basepath = getBasepath();
        scanFiles(basepath, resourceLimits);
        Collection<FileInfo> missingFiles = beginTransaction(resourceLimits);
        uploadFiles(failOnPrecompilationError, basepath, missingFiles);
        commit();
        clientDeploySender.logClientDeploy(true, addVersionToArgs());
      } finally {
        rollback();
      }
    } catch (HttpIoException e) {
      if (e.isSlaError()) {
        clientDeploySender.logClientDeploy(false, addVersionToArgs());
      }
      throw e;
    } catch (RuntimeException e) {
      clientDeploySender.logClientDeploy(false, addVersionToArgs());
      throw e;
    }
  }

  private void uploadFiles(boolean failOnPrecompilationError, File basepath,
      Collection<FileInfo> missingFiles)
      throws LocalIOException, RemoteIOException {
    int numFiles;
    app.statusUpdate("Uploading " + missingFiles.size() + " files.", 50);
    if (!missingFiles.isEmpty()) {
      numFiles = 0;
      int quarter = Math.max(1, missingFiles.size() / 4);
      for (FileInfo missingFile : missingFiles) {
        logger.fine("Uploading file '" + missingFile + "'");
        uploadFile(missingFile);
        if (++numFiles % quarter == 0) {
          app.statusUpdate("Uploaded " + numFiles + " files.");
        }
      }
    }
    uploadErrorHandlers(app.getErrorHandlers(), basepath);
    if (app.isPrecompilationEnabled()) {
      precompile(failOnPrecompilationError);
    }
    fileBatcher.flush();
    blobBatcher.flush();
  }

  private void scanFiles(File basepath, ResourceLimits resourceLimits)
      throws LocalIOException {

    app.statusUpdate("Scanning files on local disk.", 20);
    int numFiles = 0;
    long resourceTotal = 0;
    List<Pattern> skipFiles = loadSkipFiles(app.getAppYaml());
    for (File f : new FileIterator(basepath)) {
      if (shouldSkip(f.getName(), skipFiles)) {
        continue;
      }
      FileInfo fileInfo = new FileInfo(f, basepath);
      fileInfo.setMimeType(app);

      logger.fine("Processing file '" + f + "'.");
      long maxFileBlobSize = fileInfo.mimeType != null ?
          resourceLimits.maxBlobSize() : resourceLimits.maxFileSize();
      if (f.length() > maxFileBlobSize) {
        String message;
        if (f.getName().toLowerCase().endsWith(".jar")) {
          message = "Jar " + f.getPath() + " is too large. Consider "
              + "using --enable_jar_splitting.";
        } else {
          message = "File " + f.getPath() + " is too large (limit "
              + maxFileBlobSize + " bytes).";
        }
        throw new LocalIOException(message);
      }
      resourceTotal += addFile(fileInfo);

      if (++numFiles % 250 == 0) {
        app.statusUpdate("Scanned " + numFiles + " files.");
      }
    }
    if (numFiles > resourceLimits.maxFileCount()) {
      throw new LocalIOException("Applications are limited to "
          + resourceLimits.maxFileCount() + " files, you have " + numFiles
          + ".");
    }
    if (resourceTotal > resourceLimits.maxTotalFileSize()) {
      throw new LocalIOException("Applications are limited to "
          + resourceLimits.maxTotalFileSize() + " bytes of resource files, "
          + "you have " + resourceTotal + ".");
    }
  }

  private void reportSkippingGlobalConfiguration() {
    TreeSet<String> skipSet = new TreeSet<String>();
    if (app.getIndexesXml() != null) {
      skipSet.add("indexes.xml");
    }
    if (app.getCronXml() != null) {
      skipSet.add("cron.xml");
    }
    if (app.getQueueXml() != null) {
      skipSet.add("queue.xml");
    }
    if (app.getDispatchXml() != null) {
      skipSet.add("dispatch.xml");
    }
    if (app.getDosXml() != null) {
      skipSet.add("dos.xml");
    }
    if (app.getPagespeedYaml() != null) {
      skipSet.add("pagespeed");
    }
    if (!skipSet.isEmpty()) {
     app.statusUpdate("Skipping global configurations: " + Joiner.on(", ").join(skipSet));
    }
  }

  private void reportIfSkippingDispatchConfiguration() {
    if (app.getDispatchXml() != null) {
      app.statusUpdate(
          "Skipping dispatch.xml - consider running \"appcfg.sh update_dispatch <war-dir>\"");
    }
  }

  private void uploadErrorHandlers(List<ErrorHandler> errorHandlers, File basepath)
      throws LocalIOException, RemoteIOException {
    if (!errorHandlers.isEmpty()) {
      app.statusUpdate("Uploading " + errorHandlers.size() + " file(s) "
          + "for static error handlers.");
      for (ErrorHandler handler : errorHandlers) {
        File file = new File(basepath, handler.getFile());
        FileInfo info = new FileInfo(file, basepath);
        String error = FileInfo.checkValidFilename(info.path);
        if (error != null) {
          throw new LocalIOException("Could not find static error handler: " + error);
        }
        info.mimeType = handler.getMimeType();
        String errorType = handler.getErrorCode();
        if (errorType == null) {
          errorType = "default";
        }
        send("/api/appversion/adderrorblob", info.file, info.mimeType, "path",
            errorType);
      }
    }
  }

  @VisibleForTesting
  interface SleepIfShouldRetry {
    /**
     * If precompilation should be retried given the number of errors so far then sleep and return
     * true; otherwise return false.
     * @param errorCount the number of precompilation errors seen so far.
     * @return true if precompilation should be tried.
     */
    boolean sleepIfShouldRetry(int errorCount);
  }

  private static class DefaultSleepAndRetry implements SleepIfShouldRetry {
    @Override public boolean sleepIfShouldRetry(int errorCount) {
      if (errorCount > 3) {
        return false;
      } else {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        return true;
      }
    }
  }

  @VisibleForTesting
  void setSleepIfShouldRetry(SleepIfShouldRetry sleepAndRetry) {
    this.sleepIfShouldRetry = sleepAndRetry;
  }

  public void precompile(boolean failOnPrecompilationError) throws RemoteIOException {
    app.statusUpdate("Initializing precompilation...");
    List<String> filesToCompile = new ArrayList<String>();

    boolean containsGoFiles = false;
    for (String f : this.files.keySet()) {
      boolean isGoFile = f.toLowerCase().endsWith(".go");
      if (isGoFile && !containsGoFiles) {
        containsGoFiles = true;
      }
      if (isGoFile || f.toLowerCase().endsWith(".py")) {
        filesToCompile.add(f);
      }
    }
    Collections.sort(filesToCompile);
    if (containsGoFiles) {
      failOnPrecompilationError = true;
    }

    int errorCount = 0;
    while (true) {
      try {
        filesToCompile.addAll(sendPrecompileRequest(Collections.<String>emptyList()));
        break;
      } catch (RemoteIOException ex) {
        errorCount++;
        if (!sleepIfShouldRetry.sleepIfShouldRetry(errorCount)) {
          if (failOnPrecompilationError) {
            throw precompilationFailedException("", ex);
          } else {
            logger.warning(PRECOMPILATION_FAILED_WARNING_MESSAGE);
            return;
          }
        }
      }
    }

    errorCount = 0;
    while (!filesToCompile.isEmpty()) {
      try {
        if (precompileChunk(filesToCompile)) {
          errorCount = 0;
        }
      } catch (RemoteIOException ex) {
        Collections.shuffle(filesToCompile);
        errorCount++;
        if (!sleepIfShouldRetry.sleepIfShouldRetry(errorCount)) {
          if (failOnPrecompilationError) {
            String messageFragment = " with " + filesToCompile.size() + " file(s) remaining";
            throw precompilationFailedException(messageFragment, ex);
          } else {
            logger.warning(PRECOMPILATION_FAILED_WARNING_MESSAGE);
            return;
          }
        }
      }
    }
  }

  private static RemoteIOException precompilationFailedException(
      String messageFragment, RemoteIOException cause) {
    String message = "Precompilation failed" + messageFragment + ". Consider adding"
        + " <precompilation-enabled>false</precompilation-enabled> to your appengine-web.xml"
        + " and trying again.";
    if (cause instanceof HttpIoException) {
      HttpIoException httpCause = (HttpIoException) cause;
      return new HttpIoException(message, httpCause.getResponseCode(), httpCause);
    } else {
      return RemoteIOException.from(cause, message);
    }
  }

  /**
   * Attempt to precompile up to {@code MAX_FILES_PER_PRECOMPILE} files from
   * {@code filesToCompile}.
   *
   * @param filesToCompile a list of file names, which will be mutated to remove
   *        any files that were successfully compiled.
   *
   * @return true if filesToCompile was reduced in size (i.e. progress was
   *         made).
   */
  private boolean precompileChunk(List<String> filesToCompile)
      throws RemoteIOException {
    int filesLeft = filesToCompile.size();
    if (filesLeft == 0) {
      app.statusUpdate("Initializing precompilation...");
    } else {
      app.statusUpdate(MessageFormat.format(
          "Precompiling... {0} file(s) left.", filesLeft));
    }

    List<String> subset =
        filesToCompile
            .subList(0, Math.min(filesLeft, MAX_FILES_PER_PRECOMPILE));
    List<String> remainingFiles = sendPrecompileRequest(subset);
    subset.clear();
    filesToCompile.addAll(remainingFiles);
    return filesToCompile.size() < filesLeft;
  }

  private List<String> sendPrecompileRequest(List<String> filesToCompile)
      throws RemoteIOException {
    String response =
        send("/api/appversion/precompile", Joiner.on("\n").useForNull("null").join(filesToCompile));
    if (response.length() > 0) {
      return Arrays.asList(response.split("\n"));
    } else {
      return Collections.emptyList();
    }
  }

  public void updateIndexes() throws RemoteIOException {
    if (app.getIndexesXml() != null) {
      app.statusUpdate("Uploading index definitions.");
      send("/api/datastore/index/add", getIndexYaml());
    }

  }

  public void updateCron() throws RemoteIOException {
    String yaml = getCronYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading cron jobs.");
      send("/api/datastore/cron/update", yaml);
    }
  }

  public void updateQueue() throws RemoteIOException {
    String yaml = getQueueYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading task queues.");
      send("/api/queue/update", yaml);
    }
  }

  public void updateDispatch() throws RemoteIOException {
    String yaml = getDispatchYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading dispatch entries.");
      send("/api/dispatch/update", yaml);
    }
  }

  public void updateDos() throws RemoteIOException {
    String yaml = getDosYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading DoS entries.");
      send("/api/dos/update", yaml);
    }
  }

  public void updatePagespeed() throws RemoteIOException {
    String yaml = getPagespeedYaml();
    if (yaml != null) {
      app.statusUpdate("Uploading PageSpeed entries.");
      send("/api/appversion/updatepagespeed", yaml);
    } else {
      try {
        send("/api/appversion/updatepagespeed", "");
      } catch (HttpIoException exc) {
        if (exc.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
          throw exc;
        }
      }
    }
  }

  public void setDefaultVersion() throws IOException {
    String module = app.getModule();
    String url = "/api/appversion/setdefault";
    if (module != null) {
      String[] modules = module.split(",");
      if (modules.length > 1) {
        app.statusUpdate("Setting the default version of modules " + Joiner.on(", ").join(modules) +
                         " of application " + app.getAppId() + " to " + app.getVersion());
        Multimap<String, String> args = ArrayListMultimap.create();
        args.put("app_id", app.getAppId());
        args.put("version", app.getVersion());
        for (String mod : modules) {
          args.put("module", mod);
        }
        connection.post(url, "", args);
        return;
      } else {
        app.statusUpdate("Setting the default version of module " + module + " of application " +
                         app.getAppId() + " to " + app.getVersion());
      }
    } else {
      app.statusUpdate("Setting the default version of application " + app.getAppId() +
                       " to " + app.getVersion());
    }
    send(url, "");
  }

  protected String getIndexYaml() {
    return app.getIndexesXml().toYaml();
  }

  protected String getCronYaml() {
    if (app.getCronXml() != null) {
      return app.getCronXml().toYaml();
    } else {
      return null;
    }
  }

  protected String getQueueYaml() {
    if (app.getQueueXml() != null) {
      return app.getQueueXml().toYaml();
    } else {
      return null;
    }
  }

  protected String getDispatchYaml() {
    return app.getDispatchXml() == null ? null : app.getDispatchXml().toYaml();
  }

  protected String getDosYaml() {
    if (app.getDosXml() != null) {
      return app.getDosXml().toYaml();
    } else {
      return null;
    }
  }

  protected String getPagespeedYaml() {
    return app.getPagespeedYaml();
  }

  @VisibleForTesting
  protected boolean getInTransaction() {
    return this.inTransaction;
  }

  @VisibleForTesting
  protected void setInTransaction(boolean newValue) {
    this.inTransaction = newValue;
  }

  private File getBasepath() {
    File path = app.getStagingDir();
    if (path == null) {
      path = new File(app.getPath());
    }
    return path;
  }

  /**
   * Get the URL that the user would go to for their app's logs.  This string is intended to be
   * provided to the user, to show them where to go to find an error.
   *
   * @return A URL that the user can use to find their app's logs.
   */
  @VisibleForTesting
  String getLogUrl() {
    StringBuilder url = new StringBuilder();
    url.append("https://appengine.google.com/logs?app_id=");
    url.append(app.getAppId());
    if (app.getVersion() != null) {
      url.append("&version_id=");
      if (app.getModule() != null) {
        url.append(app.getModule());
        url.append("%3A");
      }
      url.append(app.getVersion());
    }
    return url.toString();
  }

  /**
   * Adds a file for uploading, returning the bytes counted against the total
   * resource quota.
   *
   * @param info
   * @return 0 for a static file, or file.length() for a resource file.
   */
  @VisibleForTesting
  long addFile(FileInfo info) {
    if (inTransaction) {
      throw new IllegalStateException("Already in a transaction.");
    }

    String error = FileInfo.checkValidFilename(info.path);
    if (error != null) {
      logger.severe(error);
      return 0;
    }

    files.put(info.path, info);

    return info.mimeType != null ? 0 : info.file.length();
  }

  /**
   * Parses the response from /api/appversion/create into a Map.
   *
   * @param response String returned from the /api/appversion/create call.
   * @return YAML parsed into Map.
   */
  private ArrayList<String> validateBeginYaml(String response) {
    YamlReader yaml = new YamlReader(new StringReader(response));
    try {
      Object obj = yaml.read();
      if (obj != null) {
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) obj;
        if (responseMap != null) {
          obj = responseMap.get("warnings");
          if (obj != null) {
            @SuppressWarnings("unchecked")
            ArrayList<String> warnings = (ArrayList<String>) obj;
            return warnings;
          }
        }
      }
    } catch (YamlException exc) {
    } catch (ClassCastException exc) {
    }
    return new ArrayList<String>();
  }

  /**
   * Begins the transaction, returning a list of files that need uploading.
   *
   * All calls to addFile must be made before calling beginTransaction().
   *
   * @param resourceLimits is the collection of resource limits for AppCfg.
   * @return A list of pathnames that should be uploaded using uploadFile()
   *         before calling commit().
   */
  @VisibleForTesting
  Collection<FileInfo> beginTransaction(ResourceLimits resourceLimits) throws RemoteIOException {
    if (inTransaction) {
      throw new IllegalStateException("Already in a transaction.");
    }

    if (backend == null) {
      app.statusUpdate("Initiating update.");
    } else {
      app.statusUpdate("Initiating update of backend " + backend + ".");
    }
    String response = send("/api/appversion/create", app.getAppYaml());
    ArrayList<String> warnings = validateBeginYaml(response);
    for (String warning : warnings) {
      app.statusUpdate("WARNING: " + warning);
    }
    inTransaction = true;
    Collection<FileInfo> blobsToClone = new ArrayList<FileInfo>(files.size());
    Collection<FileInfo> filesToClone = new ArrayList<FileInfo>(files.size());

    for (FileInfo f : files.values()) {
      if (f.mimeType == null) {
        filesToClone.add(f);
      } else {
        blobsToClone.add(f);
      }
    }

    TreeMap<String, FileInfo> filesToUpload = new TreeMap<String, FileInfo>();
    cloneFiles("/api/appversion/cloneblobs", blobsToClone, "static",
        filesToUpload, resourceLimits.maxFilesToClone());
    cloneFiles("/api/appversion/clonefiles", filesToClone, "application",
        filesToUpload, resourceLimits.maxFilesToClone());

    logger.fine("Files to upload :");
    for (FileInfo f : filesToUpload.values()) {
      logger.fine("\t" + f);
    }

    this.files = filesToUpload;
    return new ArrayList<FileInfo>(filesToUpload.values());
  }

  private static final String LIST_DELIMITER = "\n";

  /**
   * Sends files to the given url.
   *
   * @param url server URL to use.
   * @param filesParam List of files to clone.
   * @param type Type of files ( "static" or "application")
   * @param filesToUpload Files that need to be uploaded are added to this
   *        Collection.
   * @param maxFilesToClone Max number of files to clone at a single time.
   */
  private void cloneFiles(String url, Collection<FileInfo> filesParam,
      String type, Map<String, FileInfo> filesToUpload, long maxFilesToClone)
      throws RemoteIOException {
    if (filesParam.isEmpty()) {
      return;
    }
    app.statusUpdate("Cloning " + filesParam.size() + " " + type + " files.");

    int cloned = 0;
    int remaining = filesParam.size();
    ArrayList<FileInfo> chunk = new ArrayList<FileInfo>((int) maxFilesToClone);
    for (FileInfo file : filesParam) {
      chunk.add(file);
      if (--remaining == 0 || chunk.size() >= maxFilesToClone) {
        if (cloned > 0) {
          app.statusUpdate("Cloned " + cloned + " files.");
        }
        String result = send(url, buildClonePayload(chunk));
        if (result != null && result.length() > 0) {
          for (String path : result.split(LIST_DELIMITER)) {
            if (path == null || path.length() == 0) {
              continue;
            }
            FileInfo info = this.files.get(path);
            if (info == null) {
              logger.warning("Skipping " + path + ": missing FileInfo");
              continue;
            }
            filesToUpload.put(path, info);
          }
        }
        cloned += chunk.size();
        chunk.clear();
      }
    }
  }

  /**
   * Uploads a file to the hosting service.
   *
   * Must only be called after beginTransaction(). The file provided must be on
   * of those that were returned by beginTransaction();
   *
   * @param file FileInfo for the file to upload.
   */
  private void uploadFile(FileInfo file) throws RemoteIOException {
    if (!inTransaction) {
      throw new IllegalStateException(
          "beginTransaction() must be called before uploadFile().");
    }
    if (!files.containsKey(file.path)) {
      throw new IllegalArgumentException("File " + file.path
          + " is not in the list of files to be uploaded.");
    }

    files.remove(file.path);
    if (file.mimeType == null) {
      fileBatcher.addToBatch(file);
    } else {
      blobBatcher.addToBatch(file);
    }
  }

  /**
   * Commits the transaction, making the new app version available.
   *
   * All the files returned by beginTransaction must have been uploaded with
   * uploadFile() before commit() may be called.
   */
  @VisibleForTesting
  void commit() throws RemoteIOException {
    deploy();
    try {
      boolean ready = retryWithBackoff(1, 2, 60, 20, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return isReady();
        }
      });

      if (ready) {
        startServing();
      } else {
        logger.severe("Version still not ready to serve, aborting.");
        throw new RemoteIOException("Version not ready.");
      }

      boolean versionIsServing = retryWithBackoff(1, 2, 60, 20, new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return isServing();
        }
      });
      if (!versionIsServing) {
        logger.severe("Version still not serving, aborting.");
        throw new RemoteIOException("Version not ready.");
      }
      if (checkConfigUpdated) {
        Optional<EndpointsServingStatus> result = retryWithBackoffOptional(1, 2, 60, 20,
            new IsConfigUpdatedCallable());
        checkEndpointsServingStatusResult(result);
      }
      app.statusUpdate("Closing update: new version is ready to start serving.");
      inTransaction = false;
    } catch (RemoteIOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * A Callable to check the isconfigserving endpoint to see if the Endpoints Configuration
   * has been updated.  This is intended for use with retryWithBackoffOptional.
   */
  class IsConfigUpdatedCallable implements Callable<Optional<EndpointsServingStatus>> {
    @Override
    public Optional<EndpointsServingStatus> call() throws Exception {
      EndpointsServingStatus result = isConfigUpdated();
      return result == EndpointsServingStatus.PENDING
          ? Optional.<EndpointsServingStatus>absent()
          : Optional.of(result);
    }
  }

  /**
   * Check the result of calling IsConfigUpdatedCallable.  Failed values result in a
   * RuntimeException being thrown.
   *
   * @param callResult The optional serving status to be checked.  An empty value is treated the
   *        same as a PENDING value.
   */
  @VisibleForTesting
  void checkEndpointsServingStatusResult(
      Optional<EndpointsServingStatus> callResult) {
    EndpointsServingStatus configServingStatus = callResult.or(EndpointsServingStatus.PENDING);
    if (configServingStatus != EndpointsServingStatus.SERVING) {
      final String errorMessage = String.format(
          "Endpoints configuration not updated (last result: %s).  Check the app's AppEngine logs "
          + "for errors: %s", configServingStatus, getLogUrl());
      app.statusUpdate(errorMessage);
      logger.severe(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  /**
   * Deploys the new app version but does not make it default.
   *
   * All the files returned by beginTransaction must have been uploaded with
   * uploadFile() before commit() may be called.
   */
  private void deploy() throws RemoteIOException {
    if (!inTransaction) {
      throw new IllegalStateException(
          "beginTransaction() must be called before deploy().");
    }
    if (!files.isEmpty()) {
      throw new IllegalStateException(
          "Some required files have not been uploaded.");
    }
    app.statusUpdate("Deploying new version.", 20);
    send("/api/appversion/deploy", "");
    deployed = true;
  }

  /**
   * Check if the new app version is ready to serve traffic.
   *
   * @return true if the server returned that the app is ready to serve.
   */
  private boolean isReady() throws IOException {
    if (!deployed) {
      throw new IllegalStateException(
          "deploy() must be called before isReady()");
    }
    String result = send("/api/appversion/isready", "");
    return "1".equals(result.trim());
  }

  private void startServing() throws IOException {
    if (!deployed) {
      throw new IllegalStateException(
          "deploy() must be called before startServing()");
    }
    send("/api/appversion/startserving", "", "willcheckserving", "1");
    started = true;
  }

  @VisibleForTesting
  protected Map<String, String> parseIsServingResponse(String isServingResp) {
    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    if (isServingResp.isEmpty()) {
      return result.build();
    }

    try {
      YamlReader yamlReader = new YamlReader(isServingResp);
      @SuppressWarnings("unchecked")
      Map<Object, Object> resultMap = yamlReader.read(Map.class, String.class);
      for (Object key : resultMap.keySet()) {
        result.put((String) key, (String) resultMap.get(key));
      }
    } catch (YamlException e) {
      logger.severe("Unable to parse Yaml from response: " + result);
      throw new RuntimeException(e);
    }
    return result.build();
  }

  private boolean isServing() throws IOException {
    if (!started) {
      throw new IllegalStateException(
          "startServing() must be called before isServing().");
    }
    String result = send("/api/appversion/isserving", "", "new_serving_resp", "1");
    if ("1".equals(result.trim()) || "0".equals(result.trim())) {
      return "1".equals(result.trim());
    }

    Map<String, String> resultMap = parseIsServingResponse(result.trim());
    if (resultMap.containsKey("message") &&
        !YAML_EMPTY_STRING.equals(resultMap.get("message"))) {
      app.statusUpdate(resultMap.get("message"));
    }
    if (resultMap.containsKey("fatal") &&
        Boolean.parseBoolean(resultMap.get("fatal").toLowerCase())) {
      throw new RuntimeException(
          "Fatal problem encountered during deployment. Please refer to the logs" +
          " for more information.");
    }
    if (resultMap.containsKey("check_endpoints_config")) {
      checkConfigUpdated = Boolean.parseBoolean(resultMap.get("check_endpoints_config"));
    }
    if (resultMap.containsKey("serving")) {
      return Boolean.parseBoolean(resultMap.get("serving"));
    } else {
      throw new RuntimeException(
          "Fatal problem encountered during deployment. Unexpected response when " +
          "checking for serving status. Response: " + result);
    }
  }

  @VisibleForTesting
  Map<String, String> parseIsConfigUpdatedResponse(String isConfigUpdatedResp) {
    ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
    try {
      YamlReader yamlReader = new YamlReader(isConfigUpdatedResp);
      @SuppressWarnings("unchecked")
      Map<Object, Object> resultMap = yamlReader.read(Map.class, String.class);
      if (resultMap == null) {
        return result.build();
      }

      for (Object key : resultMap.keySet()) {
        result.put((String) key, (String) resultMap.get(key));
      }
    } catch (YamlException e) {
      logger.severe("Unable to parse Yaml from response: " + result);
      throw new RuntimeException(e);
    }
    return result.build();
  }

  private EndpointsServingStatus isConfigUpdated() throws IOException, IllegalArgumentException {
    if (!started) {
      throw new IllegalStateException(
          "startServing() must be called before isConfigUpdated().");
    }
    String result = send("/api/isconfigupdated", "");

    Map<String, String> resultMap = parseIsConfigUpdatedResponse(result.trim());
    if (resultMap.containsKey("updatedDetail")) {
      return EndpointsServingStatus.parse(resultMap.get("updatedDetail"));
    } else if (resultMap.containsKey("updated")) {
      return Boolean.parseBoolean(resultMap.get("updated"))
          ? EndpointsServingStatus.SERVING
          : EndpointsServingStatus.PENDING;
    } else {
      throw new RuntimeException(
          "Fatal problem encountered during deployment. Unexpected response when " +
          "checking for configuration update status. Response: " + result);
    }
  }

  public void forceRollback() throws RemoteIOException {
    app.statusUpdate("Rolling back the update" + (this.backend == null ? "."
        : " on backend " + this.backend + "."));
    send("/api/appversion/rollback", "");
  }

  private void rollback() throws RemoteIOException {
    if (!inTransaction) {
      return;
    }
    forceRollback();
  }

  @VisibleForTesting
  String send(String url, String payload, String... args)
      throws RemoteIOException {
    try {
      return clientDeploySender.send(url, payload, addVersionToArgs(args));
    } catch (IOException e) {
      throw RemoteIOException.from(e);
    }
  }

  @VisibleForTesting
  String send(String url, File payload, String mimeType, String... args)
      throws RemoteIOException {
    try {
      return clientDeploySender.send(url, payload, mimeType, addVersionToArgs(args));
    } catch (IOException e) {
      throw RemoteIOException.from(e);
    }
  }

  private String[] addVersionToArgs(String... args) {
    List<String> result = new ArrayList<String>();
    Collections.addAll(result, args);
    result.add("app_id");
    result.add(app.getAppId());
    if (backend != null) {
      result.add("backend");
      result.add(backend);
    } else if (app.getVersion() != null) {
      result.add("version");
      result.add(app.getVersion());
    }
    if (app.getModule() != null) {
      result.add("module");
      result.add(app.getModule());
    }
    return result.toArray(new String[result.size()]);
  }

  /**
   * Calls a function multiple times until it returns true, backing off more and more each time.
   *
   * @param initialDelay Inital delay after the first try, in seconds.
   * @param backoffFactor Delay will be multiplied by this factor after each
   *        try.
   * @param maxDelay Maximum delay factor.
   * @param maxTries Maximum number of tries.
   * @param callable Callable to call.
   * @return true if the Callable returned true in one of its tries.
   */
  private boolean retryWithBackoff(double initialDelay, double backoffFactor,
      double maxDelay, int maxTries, final Callable<Boolean> callable)
      throws Exception {
    Optional<Boolean> result = retryWithBackoffOptional(
        initialDelay, backoffFactor, maxDelay, maxTries,
        new Callable<Optional<Boolean>>() {
          @Override
          public Optional<Boolean> call() throws Exception {
            return callable.call() ? Optional.of(true) : Optional.<Boolean>absent();
          }
        });
    return result.or(false);
  }

  /**
   * Calls a function (with an optional return value) multiple times until it returns a value,
   * backing off more and more each time.
   *
   * @param initialDelay Inital delay after the first try, in seconds.
   * @param backoffFactor Delay will be multiplied by this factor after each
   *        try.
   * @param maxDelay Maximum delay factor.
   * @param maxTries Maximum number of tries.
   * @param callable Callable to call.
   * @return the result of the last call to the Callable.  If the optional Callable return value
   *         never returns anything, the result will be an empty Optional.
   */
  @VisibleForTesting
  public <T> Optional<T> retryWithBackoffOptional(double initialDelay, double backoffFactor,
      double maxDelay, int maxTries, Callable<Optional<T>> callable)
      throws Exception {
    long delayMillis = (long) (initialDelay * 1000);
    long maxDelayMillis = (long) (maxDelay * 1000);
    Optional<T> callResult = callable.call();
    if (callResult.isPresent()) {
      return callResult;
    }
    while (maxTries > 1) {
      app.statusUpdate("Will check again in " + (delayMillis / 1000) + " seconds.");
      Thread.sleep(delayMillis);
      delayMillis *= backoffFactor;
      if (delayMillis > maxDelayMillis) {
        delayMillis = maxDelayMillis;
      }
      maxTries--;
      callResult = callable.call();
      if (callResult.isPresent()) {
        return callResult;
      }
    }
    return Optional.<T>absent();
  }

  private static final String TUPLE_DELIMITER = "|";

  /**
   * Build the post body for a clone request.
   *
   * @param files List of FileInfos for the files to clone.
   * @return A string containing the properly delimited tuples.
   */
  private static String buildClonePayload(Collection<FileInfo> files) {
    StringBuffer data = new StringBuffer();
    boolean first = true;
    for (FileInfo file : files) {
      if (first) {
        first = false;
      } else {
        data.append(LIST_DELIMITER);
      }
      data.append(file.path);
      data.append(TUPLE_DELIMITER);
      data.append(file.hash);
      if (file.mimeType != null) {
        data.append(TUPLE_DELIMITER);
        data.append(file.mimeType);
      }
    }

    return data.toString();
  }

  @VisibleForTesting
  static String getRuntime(String appYaml) {
    String result = "?";
    try {
      Map<?, ?> yaml = (Map<?, ?>) new YamlReader(appYaml).read();
      Object runtime = yaml.get("runtime");
      if (runtime instanceof String) {
        result = (String) runtime;
      }
    } catch (YamlException ex) {
      logger.severe(ex.toString());
    }
    return result;
  }

  @VisibleForTesting
  static List<Pattern> loadSkipFiles(String appYaml) {
    List<Pattern> skipFiles = new ArrayList<>();
    if (appYaml == null) {
      return skipFiles;
    }
    try {
      Map<?, ?> yaml = (Map<?, ?>) new YamlReader(appYaml).read();
      List<?> skipFileList = (List<?>) yaml.get("skip_files");
      if (skipFileList != null) {
        for (Object skipFile : skipFileList) {
          skipFiles.add(Pattern.compile(skipFile.toString()));
        }
      }
    } catch (YamlException ex) {
      logger.severe(ex.toString());
    }
    return skipFiles;
  }

  @VisibleForTesting
  static boolean shouldSkip(String name, List<Pattern> skipFiles) {
    for (Pattern skipPattern : skipFiles) {
      if (skipPattern.matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  static class FileInfo implements Comparable<FileInfo> {
    public File file;
    public String path;
    public String hash;
    public String mimeType;

    private FileInfo(String path) {
      this.path = path;
      this.mimeType = "";
    }

    public FileInfo(File f, File base) throws LocalIOException {
      this.file = f;
      this.path = Utility.calculatePath(f, base);
      this.hash = calculateHash();
    }

    @VisibleForTesting
    static FileInfo newForTesting(String path) {
      return new FileInfo(path);
    }

    public void setMimeType(GenericApplication app) {
      mimeType = app.getMimeTypeIfStatic(path);
    }

    @Override
    public String toString() {
      return (mimeType == null ? "" : mimeType) + '\t' + hash + "\t" + path;
    }

    @Override
    public int compareTo(FileInfo other) {
      return path.compareTo(other.path);
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof FileInfo) {
        return path.equals(((FileInfo) obj).path);
      }
      return false;
    }

    private static final Pattern FILE_PATH_POSITIVE_RE =
        Pattern.compile("^[ 0-9a-zA-Z._+/@$-]{1,256}$");

    private static final Pattern FILE_PATH_NEGATIVE_RE_1 =
        Pattern.compile("[.][.]|^[.]/|[.]$|/[.]/|^-|^_ah/|^/");

    private static final Pattern FILE_PATH_NEGATIVE_RE_2 =
        Pattern.compile("//|/$");

    private static final Pattern FILE_PATH_NEGATIVE_RE_3 =
        Pattern.compile("^ | $|/ | /");

    @VisibleForTesting
    static String checkValidFilename(String path) {
      if (!FILE_PATH_POSITIVE_RE.matcher(path).matches()) {
        return "Invalid character in filename: " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_1.matcher(path).find()) {
        return "Filname cannot contain '.' or '..' or start with '-', '_ah/' or '/' : " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_2.matcher(path).find()) {
        return "Filname cannot have trailing / or contain //: " + path;
      }
      if (FILE_PATH_NEGATIVE_RE_3.matcher(path).find()) {
        return "Any spaces must be in the middle of a filename: '" + path + "'";
      }
      return null;
    }

    private static final BaseEncoding SEPARATED_HEX =
        BaseEncoding.base16().lowerCase().withSeparator("_", 8);

    @VisibleForTesting
    static String calculateHash(ByteSource source) throws IOException {
      byte[] hash = source.hash(Hashing.sha1()).asBytes();
      return SEPARATED_HEX.encode(hash);
    }

    public String calculateHash() throws LocalIOException {
      try {
        return calculateHash(Files.asByteSource(file));
      } catch (IOException e) {
        throw LocalIOException.from(e);
      }
    }
  }

  class UploadBatcher {

    static final int MAX_BATCH_SIZE = 3200000;
    static final int MAX_BATCH_COUNT = 100;
    static final int MAX_BATCH_FILE_SIZE = 200000;
    static final int BATCH_OVERHEAD = 500;

    String what;
    String singleUrl;
    String batchUrl;
    boolean batching = true;
    List<FileInfo> batch = new ArrayList<FileInfo>();
    long batchSize = 0;

    /**
     * @param what     Either "file" or "blob" or "errorblob" indicating what kind of objects this
     *                 batcher uploads. Used in messages and URLs.
     * @param batching whether or not we want to really do batch.
     */
    public UploadBatcher(String what, boolean batching) {
      this.what = what;
      this.singleUrl = "/api/appversion/add" + what;
      this.batchUrl = singleUrl + "s";
      this.batching = batching;
    }

    /**
     * Send the current batch on its way and reset the batch buffer when done
     */
    public void sendBatch() throws IOException {

      app.statusUpdate(
          "Sending batch containing " + batch.size() + " "+ what +"(s) totaling " +
              batchSize / 1000 + "KB.");
      clientDeploySender.sendBatch(batchUrl, batch, batchSize, addVersionToArgs("", ""));
      batch = new ArrayList<>();
      batchSize = 0;
    }

    /**
     * Flush the current batch.
     *
     * This first attempts to send the batch as a single request; if that fails because the server
     * doesn"t support batching, the files are sent one by one, and self.batching is reset to
     * False.
     *
     * At the end, self.batch and self.batchSize are reset
     */
    public void flush() throws RemoteIOException {
      if (batch.isEmpty()) {
        return;
      }
      try {
        sendBatch();
      } catch (Exception e) {
        app.statusUpdate("Exception in flushing batch payload, so sending 1 by 1..."
            + e.getMessage());
        batching = false;
        for (FileInfo fileInfo : batch) {
          send(singleUrl, fileInfo.file, fileInfo.mimeType, "path", fileInfo.path);
        }
        batch = new ArrayList<FileInfo>();
        batchSize = 0;
      }
    }

    /**
     * Batch a file, possibly flushing first, or perhaps upload it directly.
     *
     * Args: path: The name of the file. payload: The contents of the file. mime_type: The MIME
     * Content-type of the file, or None.
     *
     * If mime_type is None, application/octet-stream is substituted. """
     */
    public void addToBatch(FileInfo fileInfo) throws RemoteIOException {

      long size = fileInfo.file.length();

      if (size <= MAX_BATCH_FILE_SIZE) {
        if ((batch.size() >= MAX_BATCH_COUNT) ||
            (batchSize + size > MAX_BATCH_SIZE)) {
          flush();
        }
        if (batching) {
          batch.add(fileInfo);
          batchSize += size + BATCH_OVERHEAD;
          return;
        }
      }
      send(singleUrl, fileInfo.file, fileInfo.mimeType, "path", fileInfo.path);

    }
  }
}

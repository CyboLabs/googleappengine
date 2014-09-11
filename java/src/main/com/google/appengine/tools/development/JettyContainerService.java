// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import static com.google.appengine.tools.development.LocalEnvironment.DEFAULT_VERSION_HOSTNAME;

import com.google.appengine.api.log.dev.DevLogHandler;
import com.google.appengine.api.log.dev.LocalLogService;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.WebModule;
import com.google.apphosting.utils.jetty.JettyLogger;
import com.google.apphosting.utils.jetty.StubSessionManager;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerWrapper;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.JettyWebXmlConfiguration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.resource.Resource;
import org.mortbay.util.Scanner;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Implements a Jetty backed {@link ContainerService}.
 *
 */
@ServiceProvider(ContainerService.class)
public class JettyContainerService extends AbstractContainerService {

  private static final Logger log = Logger.getLogger(JettyContainerService.class.getName());

  public final static String WEB_DEFAULTS_XML =
      "com/google/appengine/tools/development/webdefault.xml";

  private static final int MAX_SIMULTANEOUS_API_CALLS = 100;

  private static final Long SOFT_DEADLINE_DELAY_MS = 60000L;

  /**
   * Specify which {@link Configuration} objects should be invoked when
   * configuring a web application.
   *
   * <p>This is a subset of:
   *   org.mortbay.jetty.webapp.WebAppContext.__dftConfigurationClasses
   *
   * <p>Specifically, we've removed {@link JettyWebXmlConfiguration} which
   * allows users to use {@code jetty-web.xml} files.
   */
  private static final String CONFIG_CLASSES[] = new String[] {
        "org.mortbay.jetty.webapp.WebXmlConfiguration",
        "org.mortbay.jetty.webapp.TagLibConfiguration"
  };

  private static final String WEB_XML_ATTR =
      "com.google.appengine.tools.development.webXml";
  private static final String APPENGINE_WEB_XML_ATTR =
      "com.google.appengine.tools.development.appEngineWebXml";

  static {
    System.setProperty("org.mortbay.log.class", JettyLogger.class.getName());
  }

  private final static int SCAN_INTERVAL_SECONDS = 5;

  /**
   * Jetty webapp context.
   */
  private WebAppContext context;

  /**
   * Our webapp context.
   */
  private AppContext appContext;

  /**
   * The Jetty server.
   */
  private Server server;

  /**
   * Hot deployment support.
   */
  private Scanner scanner;

  private class JettyAppContext implements AppContext {
    @Override
    public IsolatedAppClassLoader getClassLoader() {
      return (IsolatedAppClassLoader) context.getClassLoader();
    }

    @Override
    public Permissions getUserPermissions() {
      return JettyContainerService.this.getUserPermissions();
    }

    @Override
    public Permissions getApplicationPermissions() {
      return getClassLoader().getAppPermissions();
    }

    @Override
    public Object getContainerContext() {
      return context;
    }
  }

  public JettyContainerService() {
  }

  @Override
  protected File initContext() throws IOException {
    this.context = new DevAppEngineWebAppContext(appDir, externalResourceDir, devAppServerVersion,
        apiProxyDelegate, devAppServer);
    this.appContext = new JettyAppContext();

    context.setDescriptor(webXmlLocation == null ? null : webXmlLocation.getAbsolutePath());

    String webDefaultXml = devAppServer.getServiceProperties().get("appengine.webdefault.xml");
    if (webDefaultXml == null) {
      webDefaultXml = WEB_DEFAULTS_XML;
    }
    context.setDefaultsDescriptor(webDefaultXml);

    context.setConfigurationClasses(CONFIG_CLASSES);

    File appRoot = determineAppRoot();
    installLocalInitializationEnvironment();

    URL[] classPath = getClassPathForApp(appRoot);
    context.setClassLoader(new IsolatedAppClassLoader(appRoot, externalResourceDir, classPath,
        JettyContainerService.class.getClassLoader()));
    if (Boolean.parseBoolean(System.getProperty("appengine.allowRemoteShutdown"))) {
      context.addServlet(new ServletHolder(new ServerShutdownServlet()), "/_ah/admin/quit");
    }

    return appRoot;
  }

  static class ServerShutdownServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.getWriter().println("Shutting down local server.");
      resp.flushBuffer();
      DevAppServer server = (DevAppServer) getServletContext().getAttribute(
          "com.google.appengine.devappserver.Server");
      server.gracefulShutdown();
    }
  }

  @Override
  protected void connectContainer() throws Exception {
    moduleConfigurationHandle.checkEnvironmentVariables();

    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);

    try {
      SelectChannelConnector connector = new SelectChannelConnector();
      connector.setHost(address);
      connector.setPort(port);
      connector.setSoLingerTime(0);
      connector.open();

      server = new Server();
      server.addConnector(connector);

      port = connector.getLocalPort();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  protected void startContainer() throws Exception {
    context.setAttribute(WEB_XML_ATTR, webXml);
    context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);

    try {
      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      apiHandler.setHandler(context);

      server.setHandler(apiHandler);
      SessionHandler handler = context.getSessionHandler();
      if (isSessionsEnabled()) {
        handler.setSessionManager(new SerializableObjectsOnlyHashSessionManager());
      } else {
        handler.setSessionManager(new StubSessionManager());
      }
      server.start();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  protected void stopContainer() throws Exception {
    server.stop();
  }

  /**
   *  If the property "appengine.fullscan.seconds" is set to a positive integer, the web app
   *  content (deployment descriptors, classes/ and lib/) is scanned for changes that will trigger
   *  the reloading of the application.
   *  If the property is not set (default), we monitor the webapp war file or the
   * appengine-web.xml in case of a pre-exploded webapp directory, and reload the webapp whenever an
   * update is detected, i.e. a newer timestamp for the monitored file. As a single-context
   * deployment, add/delete is not applicable here.
   *
   * appengine-web.xml will be reloaded too. However, changes that require a module instance
   * restart, e.g. address/port, will not be part of the reload.
   */
  @Override
  protected void startHotDeployScanner() throws Exception {
    String fullScanInterval = System.getProperty("appengine.fullscan.seconds");
    if (fullScanInterval != null) {
      try {
        int interval =  Integer.parseInt(fullScanInterval);
        if (interval < 1) {
          log.info("Full scan of the web app for changes is disabled.");
          return;
        }
        log.info("Full scan of the web app in place every " + interval + "s.");
        fullWebAppScanner(interval);
        return;
      } catch (NumberFormatException ex) {
        log.log(Level.WARNING, "appengine.fullscan.seconds property is not an integer:", ex);
        log.log(Level.WARNING, "Using the default scanning method.");
      }
    }
    scanner = new Scanner();
    scanner.setScanInterval(SCAN_INTERVAL_SECONDS);
    scanner.setScanDirs(ImmutableList.of(getScanTarget()));
    scanner.setFilenameFilter(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        try {
          if (name.equals(getScanTarget().getName())) {
            return true;
          }
          return false;
        }
        catch (Exception e) {
          return false;
        }
      }
    });
    scanner.scan();
    scanner.addListener(new ScannerListener());
    scanner.start();
  }

  @Override
  protected void stopHotDeployScanner() throws Exception {
    if (scanner != null) {
      scanner.stop();
    }
    scanner = null;
  }

  private class ScannerListener implements Scanner.DiscreteListener {
    @Override
    public void fileAdded(String filename) throws Exception {
      fileChanged(filename);
    }

    @Override
    public void fileChanged(String filename) throws Exception {
      log.info(filename + " updated, reloading the webapp!");
      reloadWebApp();
    }

    @Override
    public void fileRemoved(String filename) throws Exception {
    }
  }

  /**
   * To minimize the overhead, we point the scanner right to the single file in question.
   */
  private File getScanTarget() throws Exception {
    if (appDir.isFile() || context.getWebInf() == null) {
      return appDir;
    } else {
      return new File(context.getWebInf().getFile().getPath()
          + File.separator + "appengine-web.xml");
    }
  }

  private void fullWebAppScanner(int interval) throws IOException {
    String webInf = context.getWebInf().getFile().getPath();
    List<File> scanList = new ArrayList<File>();
    Collections.addAll(scanList,
        new File(webInf, "classes"),
        new File(webInf, "lib"),
        new File(webInf, "web.xml"),
        new File(webInf, "appengine-web.xml"));

    scanner = new Scanner();
    scanner.setScanInterval(interval);
    scanner.setScanDirs(scanList);
    scanner.setReportExistingFilesOnStartup(false);
    scanner.setRecursive(true);
    scanner.scan();

    scanner.addListener(new Scanner.BulkListener() {
      @Override
      public void filesChanged(List changedFiles) throws Exception {
        log.info("A file has changed, reloading the web application.");
        reloadWebApp();
      }
    });

    scanner.start();
  }

  /**
   * Assuming Jetty handles race condition nicely, as this is how Jetty handles a hot deploy too.
   */
  @Override
  protected void reloadWebApp() throws Exception {
    server.getHandler().stop();
    moduleConfigurationHandle.restoreSystemProperties();
    moduleConfigurationHandle.readConfiguration();
    moduleConfigurationHandle.checkEnvironmentVariables();
    extractFieldsFromWebModule(moduleConfigurationHandle.getModule());

    /** same as what's in startContainer, we need suppress the ContextClassLoader here. */
    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);
    try {
      initContext();
      installLocalInitializationEnvironment();

      if (!isSessionsEnabled()) {
        context.getSessionHandler().setSessionManager(new StubSessionManager());
      }
      context.setAttribute(WEB_XML_ATTR, webXml);
      context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      apiHandler.setHandler(context);
      server.setHandler(apiHandler);

      apiHandler.start();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  public AppContext getAppContext() {
    return appContext;
  }

  @Override
  public void forwardToServer(HttpServletRequest hrequest,
      HttpServletResponse hresponse) throws IOException, ServletException {
    log.finest("forwarding request to module: " + appEngineWebXml.getModule() + "." + instance);
    RequestDispatcher requestDispatcher =
        context.getServletContext().getRequestDispatcher(hrequest.getRequestURI());
    requestDispatcher.forward(hrequest, hresponse);
  }

  private File determineAppRoot() throws IOException {
    Resource webInf = context.getWebInf();
    if (webInf == null) {
      if (userCodeClasspathManager.requiresWebInf()) {
        throw new AppEngineConfigException(
            "Supplied application has to contain WEB-INF directory.");
      }
      return appDir;
    }
    return webInf.getFile().getParentFile();
  }

  /**
   * {@code ApiProxyHandler} wraps around an existing {@link  org.mortbay.jetty.Handler}
   * and surrounds each top-level request (i.e. not includes or
   * forwards) with a try finally block that maintains the {@link
   * com.google.apphosting.api.ApiProxy.Environment} {@link ThreadLocal}.
   */
  private class ApiProxyHandler extends HandlerWrapper {
    @SuppressWarnings("hiding")
    private final AppEngineWebXml appEngineWebXml;

    ApiProxyHandler(AppEngineWebXml appEngineWebXml) {
      this.appEngineWebXml = appEngineWebXml;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(String target,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       int dispatch) throws IOException, ServletException {
      if (dispatch == REQUEST) {
        long startTimeUsec = System.currentTimeMillis() * 1000;
        Semaphore semaphore = new Semaphore(MAX_SIMULTANEOUS_API_CALLS);

        LocalEnvironment env = new LocalHttpRequestEnvironment(appEngineWebXml.getAppId(),
            WebModule.getModuleName(appEngineWebXml), appEngineWebXml.getMajorVersionId(),
            instance, getPort(), request, SOFT_DEADLINE_DELAY_MS, modulesFilterHelper);
        env.getAttributes().put(LocalEnvironment.API_CALL_SEMAPHORE, semaphore);
        env.getAttributes().put(DEFAULT_VERSION_HOSTNAME, "localhost:" + devAppServer.getPort());

        ApiProxy.setEnvironmentForCurrentThread(env);

        RecordingResponseWrapper wrappedResponse = new RecordingResponseWrapper(response);
        try {
          super.handle(target, request, wrappedResponse, dispatch);
          if (request.getRequestURI().startsWith(_AH_URL_RELOAD)) {
            try {
              reloadWebApp();
              log.info("Reloaded the webapp context: " + request.getParameter("info"));
            } catch (Exception ex) {
              log.log(Level.WARNING, "Failed to reload the current webapp context.", ex);
            }
          }
        } finally {
          try {
            semaphore.acquire(MAX_SIMULTANEOUS_API_CALLS);
          } catch (InterruptedException ex) {
            log.log(Level.WARNING, "Interrupted while waiting for API calls to complete:", ex);
          }
          env.callRequestEndListeners();

          if (apiProxyDelegate instanceof ApiProxyLocal) {
            ApiProxyLocal apiProxyLocal = (ApiProxyLocal) apiProxyDelegate;
            try {
              String appId = env.getAppId();
              String versionId = env.getVersionId();
              String requestId = DevLogHandler.getRequestId();
              long endTimeUsec = new Date().getTime() * 1000;

              LocalLogService logService = (LocalLogService)
                  apiProxyLocal.getService(LocalLogService.PACKAGE);

              logService.addRequestInfo(appId, versionId, requestId,
                  request.getRemoteAddr(), request.getRemoteUser(),
                  startTimeUsec, endTimeUsec, request.getMethod(),
                  request.getRequestURI(), request.getProtocol(),
                  request.getHeader("User-Agent"), true,
                  wrappedResponse.getStatus(), request.getHeader(HttpHeaders.REFERER));
              logService.clearResponseSize();
            } finally {
              ApiProxy.clearEnvironmentForCurrentThread();
            }
          }
        }
      } else {
        super.handle(target, request, response, dispatch);
      }
    }
  }

  private class RecordingResponseWrapper extends HttpServletResponseWrapper {
    private int status = SC_OK;

    RecordingResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setStatus(int sc) {
      status = sc;
      super.setStatus(sc);
    }

    public int getStatus() {
      return status;
    }

    @Override
    public void sendError(int sc) throws IOException {
      status = sc;
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      status = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        status = SC_MOVED_TEMPORARILY;
        super.sendRedirect(location);
    }

    @Override
    public void setStatus(int status, String string) {
        super.setStatus(status, string);
        this.status = status;
    }

    @Override
    public void reset() {
        super.reset();
        this.status = SC_OK;
    }
  }
}

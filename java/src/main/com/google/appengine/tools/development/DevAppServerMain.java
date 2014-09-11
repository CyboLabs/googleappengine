// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.tools.info.SdkInfo;
import com.google.appengine.tools.info.UpdateCheck;
import com.google.appengine.tools.plugins.SDKPluginManager;
import com.google.appengine.tools.plugins.SDKRuntimePlugin;
import com.google.appengine.tools.plugins.SDKRuntimePlugin.ApplicationDirectories;
import com.google.appengine.tools.util.Action;
import com.google.appengine.tools.util.Option;
import com.google.appengine.tools.util.Parser;
import com.google.appengine.tools.util.Parser.ParseResult;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The command-line entry point for DevAppServer.
 *
 */
public class DevAppServerMain extends SharedMain {

  public static final String EXTERNAL_RESOURCE_DIR_ARG = "external_resource_dir";
  public static final String GENERATE_WAR_ARG = "generate_war";
  public static final String GENERATED_WAR_DIR_ARG = "generated_war_dir";

  private static final String DEFAULT_RDBMS_PROPERTIES_FILE = ".local.rdbms.properties";
  private static final String RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY = "rdbms.properties.file";

  private static final String SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX =
      "com.google.appengine.devappserver_module.";

  private final Action ACTION = new StartAction();

  private String versionCheckServer = SdkInfo.getDefaultServer();

  private String address = DevAppServer.DEFAULT_HTTP_ADDRESS;
  private int port = DevAppServer.DEFAULT_HTTP_PORT;
  private boolean disableUpdateCheck;
  private String generatedDirectory = null;
  private String defaultGcsBucketName = null;

  /**
   * Returns the list of built-in {@link Option Options} for the given instance of
   * {@link DevAppServerMain}. The built-in options are those that are independent of any
   * {@link SDKRuntimePlugin SDKRuntimePlugins} that may be installed.
   *
   * @param main The instance of {@code DevAppServerMain} for which the built-in options are being
   *        requested. This may be {@code null} if {@link Option#apply()} will never be invoked on
   *        any of the returned {@code Options}.
   * @return The list of built-in options
   */
  @VisibleForTesting
  List<Option> getBuiltInOptions() {
    List<Option> options = new ArrayList<>();
    options.addAll(getSharedOptions());
    options.addAll(Arrays.asList(
        new Option("s", "server", false) {
          @Override
          public void apply() {
            versionCheckServer = getValue();
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --server=SERVER            The server to use to determine the latest",
                "  -s SERVER                   SDK version.");
          }
        },
        new Option("a", "address", false) {
          @Override
          public void apply() {
            address = getValue();
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --address=ADDRESS          The address of the interface on the local machine",
                "  -a ADDRESS                  to bind to (or 0.0.0.0 for all interfaces).");
          }
        },
        new Option("p", "port", false) {
          @Override
          public void apply() {
            port = Integer.valueOf(getValue());
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --port=PORT                The port number to bind to on the local machine.",
                "  -p PORT");
          }
        },
        new Option(null, "disable_update_check", true) {
          @Override
          public void apply() {
            disableUpdateCheck = true;
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --disable_update_check     Disable the check for newer SDK versions.");
          }
        },
        new Option(null, "generated_dir", false) {
          @Override
          public void apply() {
            generatedDirectory = getValue();
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --generated_dir=DIR        Set the directory where generated files are created.");
          }
        },
        new Option(null, "default_gcs_bucket", false) {
          @Override
          public void apply() {
            defaultGcsBucketName = getValue();
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --default_gcs_bucket=NAME  Set the default Google Cloud Storage bucket name.");
          }
        },
        new Option(null, "instance_port", false) {
          @Override
          public void apply() {
            processInstancePorts(getValues());
          }
        }
    ));
    return options;
  }

  private static void processInstancePorts(List<String> optionValues) {
    for (String optionValue : optionValues) {
      String[] keyAndValue = optionValue.split("=", 2);
      if (keyAndValue.length != 2) {
        reportBadInstancePortValue(optionValue);
      }

      try {
        Integer.parseInt(keyAndValue[1]);
      } catch (NumberFormatException nfe) {
        reportBadInstancePortValue(optionValue);
      }

      System.setProperty(
          SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX + keyAndValue[0].trim() + ".port",
          keyAndValue[1].trim());
    }
  }

  private static void reportBadInstancePortValue(String optionValue) {
    throw new IllegalArgumentException("Invalid instance_port value " + optionValue);
  }

  /**
   * Builds the complete list of {@link Option Options} for the given instance of
   * {@link DevAppServerMain}. The list consists of the built-in options, possibly modified and
   * extended by any {@link SDKRuntimePlugin SDKRuntimePlugins} that may be installed.
   *
   * @param main The instance of {@code DevAppServerMain} for which the options are being requested.
   *        This may be {@code null} if {@link Option#apply()} will never be invoked on any of the
   *        returned {@code Options}.
   * @return The list of all options
   */
  private List<Option> buildOptions() {
    List<Option> options = getBuiltInOptions();
    for (SDKRuntimePlugin runtimePlugin : SDKPluginManager.findAllRuntimePlugins()) {
      options = runtimePlugin.customizeDevAppServerOptions(options);
    }
    return options;
  }

  public static void main(String args[]) throws Exception {
    SharedMain.sharedInit();
    new DevAppServerMain().run(args);
  }

  public DevAppServerMain() {
  }

  public void run(String[] args) throws Exception {
    Parser parser = new Parser();
    ParseResult result = parser.parseArgs(ACTION, buildOptions(), args);
    result.applyArgs();
  }

  @Override
  public void printHelp(PrintStream out) {
    out.println("Usage: <dev-appserver> [options] <app directory>");
    out.println("");
    out.println("Options:");
    for (Option option : buildOptions()) {
      for (String helpString : option.getHelpLines()) {
        out.println(helpString);
      }
    }
    out.println(" --jvm_flag=FLAG            Pass FLAG as a JVM argument. May be repeated to");
    out.println("                              supply multiple flags.");
  }

  class StartAction extends Action {
    StartAction() {
      super("start");
    }

    @Override
    public void apply() {
      List<String> args = getArgs();
      try {
        File externalResourceDir = getExternalResourceDir();
        if (args.size() != 1) {
          printHelp(System.err);
          System.exit(1);
        }
        File appDir = new File(args.get(0)).getCanonicalFile();
        validateWarPath(appDir);

        SDKRuntimePlugin runtimePlugin = SDKPluginManager.findRuntimePlugin(appDir);
        if (runtimePlugin != null) {
          ApplicationDirectories appDirs = runtimePlugin.generateApplicationDirectories(appDir);
          appDir = appDirs.getWarDir();
          externalResourceDir = appDirs.getExternalResourceDir();
        }
        UpdateCheck updateCheck = new UpdateCheck(versionCheckServer, appDir, true);
        if (updateCheck.allowedToCheckForUpdates() && !disableUpdateCheck) {
          updateCheck.maybePrintNagScreen(System.err);
        }
        updateCheck.checkJavaVersion(System.err);

        DevAppServer server = new DevAppServerFactory().createDevAppServer(appDir,
            externalResourceDir, address, port, getNoJavaAgent());

        Map<String, String> stringProperties = getSystemProperties();
        setGeneratedDirectory(stringProperties);
        setRdbmsPropertiesFile(stringProperties, appDir, externalResourceDir);
        postServerActions(stringProperties);
        setDefaultGcsBucketName(stringProperties);
        addPropertyOptionToProperties(stringProperties);
        server.setServiceProperties(stringProperties);

        try {
          server.start().await();
        } catch (InterruptedException e) {
        }

        System.out.println("Shutting down.");
        System.exit(0);
      } catch (Exception ex) {
        ex.printStackTrace();
        System.exit(1);
      }
    }

    private void setGeneratedDirectory(Map<String, String> stringProperties) {
      if (generatedDirectory != null) {
        File dir = new File(generatedDirectory);
        String error = null;
        if (dir.exists()) {
          if (!dir.isDirectory()) {
            error = generatedDirectory + " is not a directory.";
          } else if (!dir.canWrite()) {
            error = generatedDirectory + " is not writable.";
          }
        } else if (!dir.mkdirs()) {
          error = "Could not make " + generatedDirectory;
        }
        if (error != null) {
          System.err.println(error);
          System.exit(1);
        }
       stringProperties.put(GenerationDirectory.GENERATED_DIR_PROPERTY, generatedDirectory);
      }
    }

    private void setDefaultGcsBucketName(Map<String, String> stringProperties) {
      if (defaultGcsBucketName != null) {
        stringProperties.put("appengine.default.gcs.bucket.name", defaultGcsBucketName);
      }
    }

    /**
     * Sets the property named {@link #RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY} to the default value
     * {@link #DEFAULT_RDBMS_PROPERTIES_FILE} if the property is not already set and if there is a
     * file by that name in either {@code appDir} or {@code externalResourceDir}.
     *
     * @param stringProperties The map in which the value will be set
     * @param appDir The appDir, aka the WAR dir
     * @param externalResourceDir the external resource dir, or {@code null} if there is not one.
     */
   private void setRdbmsPropertiesFile(
        Map<String, String> stringProperties, File appDir, File externalResourceDir) {
      if (stringProperties.get(RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY) != null) {
        return;
      }
      File file = findRdbmsPropertiesFile(externalResourceDir);
      if (file == null) {
        file = findRdbmsPropertiesFile(appDir);
      }
      if (file != null) {
        String path = file.getPath();
        System.out.println("Reading local rdbms properties from " + path);
        stringProperties.put(RDBMS_PROPERTIES_FILE_SYSTEM_PROPERTY, path);
      }
    }

    /**
     * Returns the default rdbms properties file in the given dir if it exists.
     * @param dir The directory in which to look
     * @return The default rdbs properties file, or {@code null}.
     */
    private File findRdbmsPropertiesFile(File dir) {
      File candidate = new File(dir, DEFAULT_RDBMS_PROPERTIES_FILE);
      if (candidate.isFile() && candidate.canRead()) {
        return candidate;
      }
      return null;
    }
  }
}

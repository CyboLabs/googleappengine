// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.tools.util.Logging;
import com.google.appengine.tools.util.Option;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.awt.Toolkit;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Contains operations common to the {@linkplain DevAppServerMain Java dev app server startup} and
 * the {@linkplain com.google.appengine.tools.development.devappserver2.StandaloneInstance
 * devappserver2 subprocess instance}.
 */
public abstract class SharedMain {

  private static String originalTimeZone;

  private boolean disableRestrictedCheck = false;
  private boolean noJavaAgent = false;
  private String externalResourceDir = null;
  private List<String> propertyOptions = null;

  /**
   * Returns the list of built-in {@link Option Options} that apply to both the monolithic dev app
   * server (in the Java SDK) and instances running under the Python devappserver2.
   */
  protected List<Option> getSharedOptions() {
    return Arrays.asList(
        new Option("h", "help", true) {
          @Override
          public void apply() {
            printHelp(System.err);
            System.exit(0);
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --help, -h                 Show this help message and exit.");
          }
        },
        new Option(null, "sdk_root", false) {
          @Override
          public void apply() {
            System.setProperty("appengine.sdk.root", getValue());
          }
          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --sdk_root=DIR             Overrides where the SDK is located.");
          }
        },
        new Option(null, "disable_restricted_check", true) {
          @Override
          public void apply() {
            disableRestrictedCheck = true;
          }
        },
        new Option(null, DevAppServerMain.EXTERNAL_RESOURCE_DIR_ARG, false) {
          @Override
          public void apply() {
            externalResourceDir = getValue();
          }
        },
        new Option(null, "property", false) {
          @Override
          public void apply() {
            propertyOptions = getValues();
          }
        },
        new Option(null, "allow_remote_shutdown", true) {
          @Override
          public void apply() {
            System.setProperty("appengine.allowRemoteShutdown", "true");

          }
        },
        new Option(null, "no_java_agent", true) {
          @Override
          public void apply() {
            noJavaAgent = true;
          }
        }
    );
  }

  protected static void sharedInit() {
    recordTimeZone();
    Logging.initializeLogging();
    if (System.getProperty("os.name").equalsIgnoreCase("Mac OS X")) {
      Toolkit.getDefaultToolkit();
    }
  }

  /**
   * We attempt to record user.timezone before the JVM alters its value.
   * This can happen just by asking for
   * {@link java.util.TimeZone#getDefault()}.
   *
   * We need this information later, so that we can know if the user
   * actually requested an override of the timezone. We can still be wrong
   * about this, for example, if someone directly or indirectly calls
   * {@code TimeZone.getDefault} before the main method to this class.
   * This doesn't happen in the App Engine tools themselves, but might
   * theoretically happen in some third-party tool that wraps the App Engine
   * tools. In that case, they can set {@code appengine.user.timezone}
   * to override what we're inferring for user.timezone.
   */
  private static void recordTimeZone() {
    originalTimeZone = System.getProperty("user.timezone");
  }

  protected abstract void printHelp(PrintStream out);

  protected void postServerActions(Map<String, String> stringProperties) {
    setTimeZone(stringProperties);
    if (disableRestrictedCheck) {
      stringProperties.put("appengine.disableRestrictedCheck", "");
    }
  }

  protected void addPropertyOptionToProperties(Map<String, String> properties) {
    properties.putAll(parsePropertiesList(propertyOptions));
  }

  protected Map<String, String> getSystemProperties() {
    Properties properties = System.getProperties();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
        throw new IllegalArgumentException("Non-string property " + entry.getKey());
      }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<String, String> stringProperties = (Map<String, String>) (Map) properties;
    return Collections.checkedMap(stringProperties, String.class, String.class);
  }

  private void setTimeZone(Map<String, String> serviceProperties) {
    String timeZone = serviceProperties.get("appengine.user.timezone");
    if (timeZone != null) {
      TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    } else {
      timeZone = originalTimeZone;
    }
    serviceProperties.put("appengine.user.timezone.impl", timeZone);
  }

  protected File getExternalResourceDir() {
    if (externalResourceDir == null) {
      return null;
    }
    externalResourceDir = externalResourceDir.trim();
    String error = null;
    File dir = null;
    if (externalResourceDir.isEmpty()) {
      error = "The empty string was specified for external_resource_dir";
    } else {
      dir = new File(externalResourceDir);
      if (dir.exists()) {
        if (!dir.isDirectory()) {
          error = externalResourceDir + " is not a directory.";
        }
      } else {
        error = "No such directory: " + externalResourceDir;
      }
    }
    if (error != null) {
      System.err.println(error);
      System.exit(1);
    }
    return dir;
  }

  public void validateWarPath(File war) {
    if (!war.exists()) {
      System.out.println("Unable to find the webapp directory " + war);
      printHelp(System.err);
      System.exit(1);
    } else if (!war.isDirectory()) {
      System.out.println("dev_appserver only accepts webapp directories, not war files.");
      printHelp(System.err);
      System.exit(1);
    }
  }

  /**
   * Parse the properties list. Each string in the last may take the the form:
   *   name=value
   *   name          shorthand for name=true
   *   noname        shorthand for name=false
   *   name=         required syntax to specify an empty value
   *
   * @param properties A list of unparsed properties (may be null).
   * @returns A map from property names to values.
   */
  @VisibleForTesting
  static Map<String, String> parsePropertiesList(List<String> properties) {
    Map<String, String> parsedProperties = new HashMap<>();
    if (properties != null) {
      for (String property : properties) {
        String[] propertyKeyValue = property.split("=", 2);
        if (propertyKeyValue.length == 2) {
          parsedProperties.put(propertyKeyValue[0], propertyKeyValue[1]);
        } else if (propertyKeyValue[0].startsWith("no")) {
          parsedProperties.put(propertyKeyValue[0].substring(2), "false");
        } else {
          parsedProperties.put(propertyKeyValue[0], "true");
        }
      }
    }
    return parsedProperties;
  }

  protected boolean getNoJavaAgent() {
    return noJavaAgent;
  }
}

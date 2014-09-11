package com.google.apphosting.utils.config;

import com.google.common.base.StringUtil;

import java.io.File;

/**
 * Holder for information for a web module extracted from the module's
 * on disk application directory.
 *
 */
public class WebModule {
  /**
   * Default value for a module name.
   */
  public static final String DEFAULT_MODULE_NAME = "default";

  private final File applicationDirectory;
  private final AppEngineWebXml appEngineWebXml;
  private final File appEngineWebXmlFile;
  private final WebXml webXml;
  private final File webXmlFile;
  private final String contextRoot;

  /**
   * Returns the server name specified in {@link #getAppEngineWebXml()} or
   * {@link #DEFAULT_SERVER_NAME} if none is specified.
   */
  public static String getModuleName(AppEngineWebXml appEngineWebXml) {
    return StringUtil.isEmptyOrWhitespace(appEngineWebXml.getModule()) ?
        "default" : appEngineWebXml.getModule().trim();
  }

  WebModule(File applicationDirectory, AppEngineWebXml appEngineWebXml, File appEngineWebXmlFile,
      WebXml webXml, File webXmlFile,  String contextRoot) {
    this.applicationDirectory = applicationDirectory;
    this.appEngineWebXml = appEngineWebXml;
    this.appEngineWebXmlFile = appEngineWebXmlFile;
    this.webXml = webXml;
    this.webXmlFile = webXmlFile;
    this.contextRoot = contextRoot;
  }

  public File getApplicationDirectory() {
    return applicationDirectory;
  }

  public AppEngineWebXml getAppEngineWebXml() {
    return appEngineWebXml;
  }

  public File getAppEngineWebXmlFile() {
    return appEngineWebXmlFile;
  }

  public WebXml getWebXml() {
    return webXml;
  }

  public File getWebXmlFile() {
    return webXmlFile;
  }

  public String getContextRoot() {
    return contextRoot;
  }

  /**
   * Returns the module name specified in {@link #getAppEngineWebXml()} or
   * {@link #DEFAULT_MODULE_NAME} if none is specified.
   */
  public String getModuleName() {
    return getModuleName(appEngineWebXml);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((appEngineWebXml == null) ? 0 : appEngineWebXml.hashCode());
    result = prime * result + ((appEngineWebXmlFile == null) ? 0 : appEngineWebXmlFile.hashCode());
    result =
        prime * result + ((applicationDirectory == null) ? 0 : applicationDirectory.hashCode());
    result = prime * result + ((contextRoot == null) ? 0 : contextRoot.hashCode());
    result = prime * result + ((webXml == null) ? 0 : webXml.hashCode());
    result = prime * result + ((webXmlFile == null) ? 0 : webXmlFile.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    WebModule other = (WebModule) obj;
    if (appEngineWebXml == null) {
      if (other.appEngineWebXml != null) {
        return false;
      }
    } else if (!appEngineWebXml.equals(other.appEngineWebXml)) {
      return false;
    }
    if (appEngineWebXmlFile == null) {
      if (other.appEngineWebXmlFile != null) {
        return false;
      }
    } else if (!appEngineWebXmlFile.equals(other.appEngineWebXmlFile)) {
      return false;
    }
    if (applicationDirectory == null) {
      if (other.applicationDirectory != null) {
        return false;
      }
    } else if (!applicationDirectory.equals(other.applicationDirectory)) {
      return false;
    }
    if (contextRoot == null) {
      if (other.contextRoot != null) {
        return false;
      }
    } else if (!contextRoot.equals(other.contextRoot)) {
      return false;
    }
    if (webXml == null) {
      if (other.webXml != null) {
        return false;
      }
    } else if (!webXml.equals(other.webXml)) {
      return false;
    }
    if (webXmlFile == null) {
      if (other.webXmlFile != null) {
        return false;
      }
    } else if (!webXmlFile.equals(other.webXmlFile)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "WebModule: applicationDirectory=" + applicationDirectory
        + " appEngineWebXml=" + appEngineWebXml
        + " appEngineWebXmlFile=" + appEngineWebXmlFile
        + " webXml=" + webXml
        + " webXmlFile=" + webXmlFile
        + " contextRoot=" + contextRoot;
  }
}
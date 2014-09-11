// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 *
 *
 */
public class WebXml {
  private final List<String> servletPatterns;
  private final List<SecurityConstraint> securityConstraints;
  private final List<String> welcomeFiles;
  private final Map<String, String> mimeMappings;
  private final Map<String, String> patternToId;
  private boolean fallThroughToRuntime;

  public WebXml() {
    servletPatterns = new ArrayList<String>();
    securityConstraints = new ArrayList<SecurityConstraint>();
    welcomeFiles = new ArrayList<String>();
    mimeMappings = new HashMap<String, String>();
    patternToId = new HashMap<String, String>();
  }

  /**
   * Returns true if {@code url} matches one of the servlets or
   * servlet filters listed in this web.xml.
   */
  public boolean matches(String url) {
    for (String pattern : servletPatterns) {
      if (pattern.length() == 0) {
        continue;
      }
      if (pattern.startsWith("*") && url.endsWith(pattern.substring(1))) {
        return true;
      } else if (pattern.endsWith("*") &&
                 url.startsWith(pattern.substring(0, pattern.length() - 1))) {
        return true;
      } else if (url.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  public String getHandlerIdForPattern(String pattern) {
    return patternToId.get(pattern);
  }

  public void addServletPattern(String urlPattern, String id) {
    YamlUtils.validateUrl(urlPattern);
    servletPatterns.add(urlPattern);
    if (id != null) {
      patternToId.put(urlPattern, id);
    }
  }

  public List<String> getServletPatterns() {
    return servletPatterns;
  }

  public List<SecurityConstraint> getSecurityConstraints() {
    return securityConstraints;
  }

  public SecurityConstraint addSecurityConstraint() {
    SecurityConstraint context = new SecurityConstraint();
    securityConstraints.add(context);
    return context;
  }

  public void addWelcomeFile(String welcomeFile) {
    welcomeFiles.add(welcomeFile);
  }

  public List<String> getWelcomeFiles() {
    return welcomeFiles;
  }

  public void addMimeMapping(String extension, String mimeType) {
    mimeMappings.put(extension, mimeType);
  }

  public Map<String, String> getMimeMappings() {
    return mimeMappings;
  }

  public String getMimeTypeForPath(String path) {
    int dot = path.lastIndexOf(".");
    if (dot != -1) {
      return mimeMappings.get(path.substring(dot + 1));
    } else {
      return null;
    }
  }

  public boolean getFallThroughToRuntime() {
    return fallThroughToRuntime;
  }

  public void setFallThroughToRuntime(boolean fallThroughToRuntime) {
    this.fallThroughToRuntime = fallThroughToRuntime;
  }

  /**
   * Performs some optional validation on this {@code WebXml}.
   *
   * @throws AppEngineConfigException If any errors are found.
   */
  public void validate() {
    for (String welcomeFile : welcomeFiles) {
      if (welcomeFile.startsWith("/")) {
        throw new AppEngineConfigException("Welcome files must be relative paths: " + welcomeFile);
      }
    }
  }

  /**
   * Information about a security context, requiring SSL and/or authentication.
   * Effectively, this is a tuple of { urlpatterns..., ssl-guarantee, auth-role }.
   */
  public static class SecurityConstraint {
    public enum RequiredRole { NONE, ANY_USER, ADMIN }
    public enum TransportGuarantee { NONE, INTEGRAL, CONFIDENTIAL }

    private final List<String> patterns;
    private TransportGuarantee transportGuarantee = TransportGuarantee.NONE;
    private RequiredRole requiredRole = RequiredRole.NONE;

    private SecurityConstraint() {
      patterns = new ArrayList<String>();
    }

    public List<String> getUrlPatterns() {
      return patterns;
    }

    public void addUrlPattern(String pattern) {
      patterns.add(pattern);
    }

    public TransportGuarantee getTransportGuarantee() {
      return transportGuarantee;
    }

    public void setTransportGuarantee(TransportGuarantee transportGuarantee) {
      this.transportGuarantee = transportGuarantee;
    }

    public RequiredRole getRequiredRole() {
      return requiredRole;
    }

    public void setRequiredRole(RequiredRole requiredRole) {
      this.requiredRole = requiredRole;
    }
  }
}

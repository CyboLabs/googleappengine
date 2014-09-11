// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;

import java.io.File;
import java.util.Map;

/**
 * Interface to backend instances
 */
public interface BackendContainer {

  public void setServiceProperties(Map<String, String> properties);

  /**
   * Shutdown all backend instances.
   *
   * @throws Exception
   */
  public void shutdownAll() throws Exception;

  /**
   * Start all backend instances.
   *
   * @param backendsXml Parsed backends.xml file with servers configuration
   * @throws Exception
   */
  public void startupAll() throws Exception;

  public void init(String address, final ModuleConfigurationHandle moduleConfigurationHandle,
      File externalResourceDirectory, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer);
}

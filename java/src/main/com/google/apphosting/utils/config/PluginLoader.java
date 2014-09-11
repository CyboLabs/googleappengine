// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Utility for loading plugins in AppConfig and DevAppserver.
 *
 */
public class PluginLoader {

  /**
   * Name of the system property used to specify the plugin search path.
   */
  public final static String PLUGIN_PATH = "com.google.appengine.plugin.path";
  private final static Logger logger = Logger.getLogger(PluginLoader.class.getName());

  private static ClassLoader loader = null;

  /**
   * Searches for plugins of the specified type.
   */
  public static <T> Iterable<T> loadPlugins(Class<T> pluginClass) {
    return ServiceLoader.load(pluginClass, getPluginClassLoader());
  }

  private synchronized static ClassLoader getPluginClassLoader() {
    if (loader == null) {
      ClassLoader parent = PluginLoader.class.getClassLoader();
      String path = System.getProperty(PLUGIN_PATH);
      if (path == null) {
        loader = parent;
      } else {
        String[] paths = path.split(File.pathSeparator);
        ArrayList<URL> urls = new ArrayList<URL>(paths.length);
        for (int i = 0; i < paths.length; ++i) {
          try {
            urls.add(new File(paths[i]).toURI().toURL());
          } catch (MalformedURLException ex) {
            logger.severe("Skipping invalid plugin path " + paths[i]);
          }
        }
        loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), parent);
      }
    }
    return loader;
  }

}

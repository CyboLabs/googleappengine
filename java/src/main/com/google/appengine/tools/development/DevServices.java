package com.google.appengine.tools.development;

/**
 * Provider for implementations of various services within the DevAppServer context.
 *
 */
public interface DevServices {
  DevLogService getLogService();
}

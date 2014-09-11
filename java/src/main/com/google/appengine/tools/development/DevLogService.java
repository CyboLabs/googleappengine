// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import java.util.logging.Handler;

/**
 * Methods common to developer-oriented implementations of the log service.
 *
 */
public interface DevLogService {
  String PACKAGE = "logservice";

  Handler getLogHandler();
}

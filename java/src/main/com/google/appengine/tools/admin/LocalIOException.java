package com.google.appengine.tools.admin;

import java.io.IOException;

/**
 * An exception that occurs while interacting with local files.
 *
 * @see RemoteIOException
 */
class LocalIOException extends IOException {
  LocalIOException(String message) {
    super(message);
  }

  LocalIOException(String message, Throwable cause) {
    super(message, cause);
  }

  static LocalIOException from(IOException ioe) {
    LocalIOException local = new LocalIOException(ioe.getMessage(), ioe.getCause());
    local.setStackTrace(ioe.getStackTrace());
    return local;
  }
}

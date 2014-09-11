package com.google.appengine.tools.admin;

import java.io.IOException;

/**
 * An exception that occurs while communicating with remote servers.
 *
 * @see LocalIOException
 */
class RemoteIOException extends IOException {
  RemoteIOException(String message) {
    this(message, null);
  }

  RemoteIOException(String message, Throwable cause) {
    super(message, cause);
  }

  static RemoteIOException from(IOException ioe) {
    if (ioe instanceof RemoteIOException) {
      return (RemoteIOException) ioe;
    } else {
      return from(ioe, ioe.getMessage());
    }
  }

  static RemoteIOException from(IOException ioe, String message) {
    return new RemoteIOException(message, ioe);
  }
}

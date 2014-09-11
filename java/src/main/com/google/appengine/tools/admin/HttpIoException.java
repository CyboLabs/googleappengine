// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

/**
 * A subclass of IOException that also carries the HTTP status code that
 * generated the exception.
 *
 *
 */
class HttpIoException extends RemoteIOException {
  /**
   * Constructs an HttpIoException (a subclass of IOException that carries
   * the HTTP status code).
   *
   * @param responseCode The HTTP response code.
   */
  HttpIoException(int responseCode) {
    super(null, null);
    this.responseCode = responseCode;
  }

  /**
   * Constructs an HttpIoException (a subclass of IOException that carries
   * the HTTP status code).
   *
   * @param message The exception's detail message.
   * @param responseCode The HTTP response code.
   */
  HttpIoException(String message, int responseCode) {
    super(message, null);
    this.responseCode = responseCode;
  }

  /**
   * Constructs an HttpIoException (a subclass of IOException that carries
   * the HTTP status code).
   *
   * @param message The exception's detail message.
   * @param responseCode The HTTP response code.
   * @param cause Another exception that caused this one, or null if none.
   */
  HttpIoException(String message, int responseCode, Throwable cause) {
    super(message, cause);
    this.responseCode = responseCode;
  }

  /**
   * Returns The HTTP response code.
   */
  int getResponseCode() {
    return responseCode;
  }

  /**
   * Returns true if this HTTP status should be counted against the SLA. Currently, we consider
   * that if the status is in the 500 range it is a server and should count against the SLA, but
   * otherwise it is likely to be a problem with the user's environment and should not.
   */
  boolean isSlaError() {
    return responseCode >= 500 && responseCode < 600;
  }

  private int responseCode;
}

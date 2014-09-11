package com.google.appengine.api.modules;

/**
 * Exception thrown by the {@link ModulesService}.
 *
 */
public class ModulesException extends RuntimeException {

  ModulesException(String detail) {
    super(detail);
  }

  ModulesException(String detail, Throwable cause) {
    super(detail, cause);
  }

  private static final long serialVersionUID = -5918019495879037930L;
}

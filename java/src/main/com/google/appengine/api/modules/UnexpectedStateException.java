package com.google.appengine.api.modules;

/**
 * Thrown to indicate {ModulesService#startVersion} was called for
 * a module version that was already started or {ModulesService#stopVersion}.
 * was called for a module version that was already stopped.
 */
class UnexpectedStateException extends ModulesException {
  private static final long serialVersionUID = -8684383201515463564L;
  UnexpectedStateException(String detail) {
    super(detail);
  }
}

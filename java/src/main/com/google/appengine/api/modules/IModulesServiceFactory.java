package com.google.appengine.api.modules;

/**
 * Factory interface for use by providers of {@link ModulesService} for
 * registration with {@link com.google.appengine.spi.ServiceFactoryFactory}.
 *
 */
public interface IModulesServiceFactory {
  /**
   * Creates and returns a {@link ModulesService}.
   */
  public ModulesService getModulesService();
}

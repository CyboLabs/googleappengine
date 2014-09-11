package com.google.appengine.api.modules;

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * Factory by which users get an implementation of the {@link ModulesService}.
 *
 * @see ModulesService
 */
public final class ModulesServiceFactory {

  /**
   * Returns an implementation of {@link ModulesService} for the current environment.
   */
  public static ModulesService getModulesService() {
    return ServiceFactoryFactory.getFactory(IModulesServiceFactory.class).getModulesService();
  }

  private ModulesServiceFactory() { }
}

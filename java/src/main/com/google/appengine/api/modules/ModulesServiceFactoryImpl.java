package com.google.appengine.api.modules;

/**
 * {@link IModulesServiceFactory} for Google AppEngine.
 *
 */
final class ModulesServiceFactoryImpl implements IModulesServiceFactory {
  @Override
  public ModulesService getModulesService() {
    return new ModulesServiceImpl();
  }
}

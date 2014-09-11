package com.google.appengine.api.modules;

import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.spi.ServiceProvider;

/**
 * Google App Engine's {@link FactoryProvider} for {@link IModulesServiceFactory}.
 *
 */
@ServiceProvider(value = FactoryProvider.class, precedence = Integer.MIN_VALUE)
public final class IModulesServiceFactoryProvider extends FactoryProvider<IModulesServiceFactory> {
  private final IModulesServiceFactory instance = new ModulesServiceFactoryImpl();

  public IModulesServiceFactoryProvider() {
    super(IModulesServiceFactory.class);
  }

  @Override
  protected IModulesServiceFactory getFactoryInstance() {
    return instance;
  }
}

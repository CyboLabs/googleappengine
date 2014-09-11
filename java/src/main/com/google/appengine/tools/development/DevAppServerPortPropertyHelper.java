package com.google.appengine.tools.development;

import java.util.Map;

/**
 * Utility methods for managing {@link DevAppServer} port related service
 * properties.
 *
 */
public class DevAppServerPortPropertyHelper {
  public static final int DEFAULT_PORT = 0;
  public static final String SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX =
      "com.google.appengine.devappserver_module.";

  private DevAppServerPortPropertyHelper() {
  }

  /**
   * Adds a service property to configure {@link DevAppServer} to use the
   * the specified port for the named module's main instance to the passed in
   * service properties. To read the added service property use
   * {@link #getPort(String, Map)}.
   */
  public static int setPort(String moduleName, int port, Map<String, String> serviceProperties) {
    return setPort(moduleName, LocalEnvironment.MAIN_INSTANCE, port, serviceProperties);
  }

  /**
   * Adds a service property to configure {@link DevAppServer} to use the
   * the specified port for the specified module instance to the passed in
   * service properties. To read the added service property use
   * {@link #getPort(String, Map)}.
   * @param moduleName the  module name.
   * @param instance the module instance or {link {@link LocalEnvironment#MAIN_INSTANCE} for the
   *        main instance.
   * @param port the port for the specified module instance.
   * @param serviceProperties the service properties.
   */
  public static int setPort(String moduleName, int instance, int port, Map<String,
      String> serviceProperties) {
    String propertyName = getInstancePropertyName(moduleName, instance);
    serviceProperties.put(propertyName, Integer.toString(port));
    return port;
  }

  /**
   * Returns the configured port for the named module's main instance from the
   * the provided service properties or {@link #DEFAULT_PORT} if no port has
   * been configured. To add the service property to configure the port use
   * {@link #setPort(String, int, Map)}.
   */
  public static int getPort(String moduleName, Map<String, String> serviceProperties) {
    return getPort(moduleName, LocalEnvironment.MAIN_INSTANCE, serviceProperties);
  }

  /**
   * Returns the configured port for the specified module instance from the
   * the provided service properties or {@link #DEFAULT_PORT} if no port has
   * been configured. To add the service property to configure the port use
   * {@link #setPort(String, int, Map)}.
   *
   * @param moduleName the module name.
   * @param instance the module instance or {link {@link LocalEnvironment#MAIN_INSTANCE} for the
   *        main instance.
   * @param serviceProperties the service properties.
   */
  public static int getPort(String moduleName, int instance,
      Map<String, String> serviceProperties) {
    String propertyName = getInstancePropertyName(moduleName, instance);
    if (serviceProperties.containsKey(propertyName)) {
      return Integer.parseInt(serviceProperties.get(propertyName));
    } else {
      return 0;
    }
  }

  private static String getInstancePropertyName(String moduleName, int instance) {
    String instanceSpec = instance == LocalEnvironment.MAIN_INSTANCE ? "" : "." + instance;
    return SYSTEM_PROPERTY_STATIC_MODULE_PORT_NUM_PREFIX + moduleName + instanceSpec + ".port";
  }
}

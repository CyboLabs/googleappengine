package com.google.appengine.tools.development;

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.apphosting.api.ApiProxy;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Abstract super class for {@link Module} implementations.
 *
 * @param <I> An {@link InstanceHolder} type which is available to a Module implementation
 * but not reflected in the {@link Module} interface.
 */
public abstract class AbstractModule<I extends InstanceHolder> implements Module {
   static final Logger LOGGER = Logger.getLogger(AbstractModule.class.getName());

  private final ModuleConfigurationHandle moduleConfigurationHandle;
  private final String serverInfo;
  private final File externalResourceDir;
  private final String address;
  private final DevAppServer devAppServer;
  private final List<I> instanceHolders;
  private ApiProxy.Delegate<?> apiProxyDelegate;

  private LocalServerEnvironment localServerEnvironment;

  protected AbstractModule(ModuleConfigurationHandle moduleConfigurationHandle,
      String serverInfo, File externalResourceDir, String address,
      DevAppServer devAppServer, List<I> instanceHolders) {
    this.moduleConfigurationHandle = moduleConfigurationHandle;
    this.serverInfo = serverInfo;
    this.externalResourceDir = externalResourceDir;
    this.address = address;
    this.devAppServer = devAppServer;
    this.instanceHolders = new CopyOnWriteArrayList<I>(instanceHolders);
  }

  @Override
  public String getModuleName() {
    return moduleConfigurationHandle.getModule().getModuleName();
  }

  protected List<I> getInstanceHolders() {
    return instanceHolders;
  }

  @Override
  public LocalServerEnvironment getLocalServerEnvironment() {
    return localServerEnvironment;
  }

  @Override
  public final void configure(Map<String, Object> containerConfigProperties) throws Exception {
    if (localServerEnvironment == null) {
        localServerEnvironment = doConfigure(moduleConfigurationHandle, serverInfo,
            externalResourceDir, address, containerConfigProperties, devAppServer);
    }
  }

  @Override
  public void createConnection() throws Exception {
    for (I instanceHolder : instanceHolders) {
      instanceHolder.createConnection();
     }
  }

  @Override
  public void setApiProxyDelegate(ApiProxy.Delegate<?> apiProxyDelegate) {
    for (I instanceHolder : instanceHolders) {
      instanceHolder.getContainerService().setApiProxyDelegate(apiProxyDelegate);
    }
  }

  @Override
  public void startup() throws Exception {
    for (I instanceHolder : instanceHolders) {
      instanceHolder.startUp();
      String listeningHostAndPort = getHostAndPort(instanceHolder);
      if (instanceHolder.isMainInstance()) {
        LOGGER.info(String.format("Module instance %s is running at http://%s/", getModuleName(),
            listeningHostAndPort));
      } else {
        LOGGER.info(String.format("Module instance %s instance %s is running at http://%s/",
            getModuleName(), instanceHolder.getInstance(), listeningHostAndPort));
      }
      LOGGER.info("The admin console is running at http://" + listeningHostAndPort + "/_ah/admin");
    }
  }

  @Override
  public String getHostAndPort(int instance) {
    I instanceHolder = getInstanceHolder(instance);
    if (instanceHolder == null) {
      return null;
    } else {
      return getHostAndPort(instanceHolder);
    }
  }

  private String getHostAndPort(I instanceHolder) {
    ContainerService containerService = instanceHolder.getContainerService();
    String prettyAddress = containerService.getAddress();
    if (prettyAddress.equals("0.0.0.0") || prettyAddress.equals("127.0.0.1")) {
      prettyAddress = "localhost";
    }
    String listeningHostAndPort = prettyAddress + ":" + containerService.getPort();
    return listeningHostAndPort;
  }

  @Override
  public I getInstanceHolder(int instance) {
    if (instance < LocalEnvironment.MAIN_INSTANCE || instance + 1 > instanceHolders.size()) {
      return null;
    } else {
      return instanceHolders.get(instance + 1);
    }
  }

  @Override
  public void shutdown() throws Exception {
    for (I instanceHolder : instanceHolders) {
      instanceHolder.getContainerService().shutdown();
      if (instanceHolder.isMainInstance()) {
        LOGGER.info("Shutting down module instance " + getModuleName());
      } else {
        LOGGER.info("Shutting down module instance " + getModuleName() + " instance "
            + instanceHolder.getInstance());
      }
    }
  }

  @Override
  public ContainerService getMainContainer() {
    return instanceHolders.get(0).getContainerService();
  }

  @Override
  public I getAndReserveAvailableInstanceHolder() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void startServing() throws Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stopServing() throws Exception {
    throw new UnsupportedOperationException();
  }

  /**
   * Configures the containers for a {@link Module} and returns the
   * {@link LocalServerEnvironment} for the main container.
   */
  protected abstract LocalServerEnvironment doConfigure(
      ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer) throws Exception;
}

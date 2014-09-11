package com.google.appengine.tools.development;

/**
 * Abstract {@link InstanceHolder}.
 */
public abstract class AbstractInstanceHolder implements InstanceHolder {
  private final ContainerService containerService;
  private final int instance;

  AbstractInstanceHolder(ContainerService containerService, int instance){
    this.containerService = containerService;
    this.instance = instance;
  }

  @Override
  public ContainerService getContainerService() {
    return containerService;
  }

  @Override
  public int getInstance() {
    return instance;
  }

  @Override
  public boolean isMainInstance() {
    return instance < 0;
  }

  @Override
  public boolean isStopped() {
    return false;
  }

  @Override
  public void createConnection() throws Exception {
    containerService.createConnection();
  }
}

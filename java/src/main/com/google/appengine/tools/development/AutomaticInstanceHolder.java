package com.google.appengine.tools.development;

/**
 * {@link InstanceHolder} for an {@link AutomaticModule}.
 */
class AutomaticInstanceHolder extends AbstractInstanceHolder {

  /**
   * Construct an instance holder.
   * @param containerService for the instance.
   * @param instance nonnegative instance number or
   *     {link {@link LocalEnvironment#MAIN_INSTANCE}.
   */
  AutomaticInstanceHolder(ContainerService containerService, int instance) {
    super(containerService, instance);
  }

  @Override
  public String toString() {
    return "AutomaticInstanceHolder: containerservice=" + getContainerService()
        + " instance=" + getInstance();
  }

  @Override
  public void startUp() throws Exception {
    getContainerService().startup();
  }

  @Override
  public boolean acquireServingPermit() {
    return true;
  }

  @Override
  public boolean isLoadBalancingInstance() {
    return false;
  }

  @Override
  public boolean expectsGeneratedStartRequest() {
    return false;
  }
}

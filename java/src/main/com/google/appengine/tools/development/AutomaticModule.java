package com.google.appengine.tools.development;

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Automatic {@link Module} implementation.
 */
class AutomaticModule extends AbstractModule<AutomaticInstanceHolder> {

  AutomaticModule(ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, DevAppServer devAppServer) {
    super(moduleConfigurationHandle, serverInfo, externalResourceDir, address,
      devAppServer, makeInstanceHolders());
  }

  private static List<AutomaticInstanceHolder> makeInstanceHolders() {
    return ImmutableList.of(new AutomaticInstanceHolder(ContainerUtils.loadContainer(),
        LocalEnvironment.MAIN_INSTANCE));
  }

  @Override
  public LocalServerEnvironment doConfigure(
      ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer) throws Exception{
    int port = DevAppServerPortPropertyHelper.getPort(getModuleName(),
        devAppServer.getServiceProperties());
    return getInstanceHolders().get(0).getContainerService().configure(serverInfo, address, port,
        moduleConfigurationHandle, externalResourceDir, containerConfigProperties,
        LocalEnvironment.MAIN_INSTANCE, devAppServer);
  }

  @Override
  public int getInstanceCount() {
    return 0;
  }
}

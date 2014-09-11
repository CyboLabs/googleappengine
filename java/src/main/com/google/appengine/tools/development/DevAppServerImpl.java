// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.appengine.api.modules.dev.LocalModulesService;
import com.google.appengine.tools.development.EnvironmentVariableChecker.MismatchReportingPolicy;
import com.google.appengine.tools.info.SdkInfo;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.EarHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code DevAppServer} launches a local Jetty server (by default) with a single
 * hosted web application.  It can be invoked from the command-line by
 * providing the path to the directory in which the application resides as the
 * only argument.
 *
 */
class DevAppServerImpl implements DevAppServer {
  public static final String MODULES_FILTER_HELPER_PROPERTY =
      "com.google.appengine.tools.development.modules_filter_helper";
  private static final Logger logger = Logger.getLogger(DevAppServerImpl.class.getName());

  private final ApplicationConfigurationManager applicationConfigurationManager;
  private final Modules modules;
  private Map<String, String> serviceProperties = new HashMap<String, String>();
  private final Map<String, Object> containerConfigProperties;
  private final int requestedPort;

  enum ServerState { INITIALIZING, RUNNING, STOPPING, SHUTDOWN }

  /**
   * The current state of the server.
   */
  private ServerState serverState = ServerState.INITIALIZING;

  /**
   * Contains the backend servers configured as part of the "Servers" feature.
   * Each backend server is started on a separate port and keep their own
   * internal state. Memcache, datastore, and other API services are shared by
   * all servers, including the "main" server.
   */
  private final BackendServers backendContainer;

  /**
   * The api proxy we created when we started the web containers. Not initialized until after
   * {@link #start()} is called.
   */
  private ApiProxyLocal apiProxyLocal;

  /**
   * We defer reporting construction time configuration exceptions until
   * {@link #start()} for compatibility.
   */
  private final AppEngineConfigException configurationException;

  /**
   * Used to schedule the graceful shutdown of the server.
   */
  private final ScheduledExecutorService shutdownScheduler = Executors.newScheduledThreadPool(1);

  /**
   * Latch that we decrement when the server is shutdown or restarted.
   * Will be {@code null} until the server is started.
   */
  private CountDownLatch shutdownLatch = null;

  /**
   * Constructs a development application server that runs the application located in the given
   * WAR or EAR directory.
   *
   * @param appDir The location of the application to run.
   * @param externalResourceDir If not {@code null}, a resource directory external to the appDir.
   *        This will be searched before appDir when looking for resources.
   * @param webXmlLocation The location of a file whose format complies with
   * http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  If {@code null},
   * defaults to <appDir>/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file.  If
   * {@code null}, defaults to <appDir>/WEB-INF/appengine-web.xml
   * @param address The address on which to run
   * @param port The port on which to run
   * @param useCustomStreamHandler If {@code true} (typical), install {@link StreamHandlerFactory}.
   * @param requestedContainerConfigProperties Additional properties used in the
   * configuration of the specific container implementation.
   */
  public DevAppServerImpl(File appDir, File externalResourceDir, File webXmlLocation,
      File appEngineWebXmlLocation, String address, int port, boolean useCustomStreamHandler,
      Map<String, Object> requestedContainerConfigProperties) {
    String serverInfo = ContainerUtils.getServerInfo();
    if (useCustomStreamHandler) {
      StreamHandlerFactory.install();
    }
    DevSocketImplFactory.install();

    backendContainer = BackendServers.getInstance();
    requestedPort = port;
    ApplicationConfigurationManager tempManager = null;
    File schemaFile = new File(SdkInfo.getSdkRoot(), "docs/appengine-application.xsd");
    try {
      if (EarHelper.isEar(appDir.getAbsolutePath())) {
        tempManager = ApplicationConfigurationManager.newEarConfigurationManager(appDir,
            SdkInfo.getLocalVersion().getRelease(), schemaFile);
       String contextRootWarning =
            "Ignoring application.xml context-root element, for details see "
             + "https://developers.google.com/appengine/docs/java/modules/#config";
        logger.info(contextRootWarning);
      } else {
        tempManager = ApplicationConfigurationManager.newWarConfigurationManager(
            appDir, appEngineWebXmlLocation, webXmlLocation, externalResourceDir,
            SdkInfo.getLocalVersion().getRelease());
      }
    } catch (AppEngineConfigException configurationException) {
      modules = null;
      applicationConfigurationManager = null;
      this.containerConfigProperties = null;
      this.configurationException = configurationException;
      return;
    }
    this.applicationConfigurationManager = tempManager;
    this.modules = Modules.createModules(applicationConfigurationManager, serverInfo,
        externalResourceDir, address, this);
    DelegatingModulesFilterHelper modulesFilterHelper =
        new DelegatingModulesFilterHelper(backendContainer, modules);
    this.containerConfigProperties = ImmutableMap.<String, Object>builder()
        .putAll(requestedContainerConfigProperties)
        .put(MODULES_FILTER_HELPER_PROPERTY, modulesFilterHelper)
        .put(AbstractContainerService.PORT_MAPPING_PROVIDER_PROP, backendContainer)
        .build();
    backendContainer.init(address,
        applicationConfigurationManager.getPrimaryModuleConfigurationHandle(),
        externalResourceDir, this.containerConfigProperties, this);
    configurationException = null;
  }

  /**
   * Sets the properties that will be used by the local services to
   * configure themselves. This method must be called before the server
   * has been started.
   *
   * @param properties a, maybe {@code null}, set of properties.
   *
   * @throws IllegalStateException if the server has already been started.
   */
  @Override
  public void setServiceProperties(Map<String, String> properties) {
    if (serverState != ServerState.INITIALIZING) {
      String msg = "Cannot set service properties after the server has been started.";
      throw new IllegalStateException(msg);
    }

    if (configurationException == null) {
      serviceProperties = new ConcurrentHashMap<String, String>(properties);
      if (requestedPort != 0) {
        DevAppServerPortPropertyHelper.setPort(modules.getMainModule().getModuleName(),
            requestedPort, serviceProperties);
      }
      backendContainer.setServiceProperties(properties);
      DevAppServerDatastorePropertyHelper.setDefaultProperties(serviceProperties);
    }
  }

  @Override
  public Map<String, String> getServiceProperties() {
    return serviceProperties;
  }

  /**
   * Starts the server.
   *
   * @throws IllegalStateException If the server has already been started or
   * shutdown.
   * @throws AppEngineConfigException If no WEB-INF directory can be found or
   * WEB-INF/appengine-web.xml does not exist.
   * @return a latch that will be decremented to zero when the server is shutdown.
   */
  @Override
  public CountDownLatch start() throws Exception {
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<CountDownLatch>() {
        @Override public CountDownLatch run() throws Exception {
          return doStart();
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  private CountDownLatch doStart() throws Exception {
    if (serverState != ServerState.INITIALIZING) {
      throw new IllegalStateException("Cannot start a server that has already been started.");
    }

    reportDeferredConfigurationException();

    initializeLogging();

    modules.configure(containerConfigProperties);
    try {
      modules.createConnections();
    } catch (BindException ex) {
      System.err.println();
      System.err.println("************************************************");
      System.err.println("Could not open the requested socket: " + ex.getMessage());
      System.err.println("Try overriding --address and/or --port.");
      System.exit(2);
    }

    ApiProxyLocalFactory factory = new ApiProxyLocalFactory();
    apiProxyLocal = factory.create(modules.getLocalServerEnvironment());
    setInboundServicesProperty();
    apiProxyLocal.setProperties(serviceProperties);
    ApiProxy.setDelegate(apiProxyLocal);
    LocalModulesService localModulesService =
        (LocalModulesService) apiProxyLocal.getService(LocalModulesService.PACKAGE);
    localModulesService.setModulesController(modules);
    installLoggingServiceHandler((DevServices) apiProxyLocal);
    TimeZone currentTimeZone = null;
    try {
      currentTimeZone = setServerTimeZone();
      backendContainer.configureAll(apiProxyLocal);
      modules.setApiProxyDelegate(apiProxyLocal);
      modules.startup();
      Module mainServer = modules.getMainModule();
      Map<String, String> portMapping = backendContainer.getPortMapping();
      AbstractContainerService.installLocalInitializationEnvironment(
          mainServer.getMainContainer().getAppEngineWebXmlConfig(), LocalEnvironment.MAIN_INSTANCE,
          getPort(), getPort(), null, -1, portMapping);
      backendContainer.startupAll();
    } finally {
      ApiProxy.clearEnvironmentForCurrentThread();
      restoreLocalTimeZone(currentTimeZone);
    }
    shutdownLatch = new CountDownLatch(1);
    serverState = ServerState.RUNNING;
    logger.info("Dev App Server is now running");
    return shutdownLatch;
  }

  private void installLoggingServiceHandler(DevServices proxy) {
    Logger root = Logger.getLogger("");
    DevLogService logService = proxy.getLogService();
    root.addHandler(logService.getLogHandler());

    Handler[] handlers = root.getHandlers();
    if (handlers != null) {
      for (Handler handler : handlers) {
        handler.setLevel(Level.FINEST);
      }
    }
  }

  public void setInboundServicesProperty() {
    ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();
    for (ApplicationConfigurationManager.ModuleConfigurationHandle moduleConfigurationHandle :
      applicationConfigurationManager.getModuleConfigurationHandles()) {
      setBuilder.addAll(
          moduleConfigurationHandle.getModule().getAppEngineWebXml().getInboundServices());
    }

    serviceProperties.put("appengine.dev.inbound-services",
        Joiner.on(",").join(setBuilder.build()));
  }

  /**
   * Change the TimeZone for the current thread. By calling this method before
   * {@link ContainerService#startup()} start}, we set the default TimeZone for the
   * DevAppServer and all of its related services.
   *
   * @return the previously installed ThreadLocal TimeZone
   */
  private TimeZone setServerTimeZone() {
    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return null;
    }

    TimeZone utc = TimeZone.getTimeZone("UTC");
    assert utc.getID().equals("UTC") : "Unable to retrieve the UTC TimeZone";

    try {
      Field f = TimeZone.class.getDeclaredField("defaultZoneTL");
      f.setAccessible(true);
      ThreadLocal<?> tl = (ThreadLocal<?>) f.get(null);
      Method getZone = ThreadLocal.class.getMethod("get");
      TimeZone previousZone = (TimeZone) getZone.invoke(tl);
      Method setZone = ThreadLocal.class.getMethod("set", Object.class);
      setZone.invoke(tl, utc);
      return previousZone;
    } catch (Exception e) {
      try {
        Method getZone = TimeZone.class.getDeclaredMethod("getDefaultInAppContext");
        getZone.setAccessible(true);
        TimeZone previousZone = (TimeZone) getZone.invoke(null);
        Method setZone = TimeZone.class.getDeclaredMethod("setDefaultInAppContext", TimeZone.class);
        setZone.setAccessible(true);
        setZone.invoke(null, utc);
        return previousZone;
      } catch (Exception ex) {
        logger.log(Level.WARNING,
            "Unable to set the TimeZone to UTC (this is expected if running on JDK 8)");
        return null;
      }
    }
  }

  /**
   * Restores the ThreadLocal TimeZone to {@code timeZone}.
   */
  private void restoreLocalTimeZone(TimeZone timeZone) {
    if (timeZone == null) {
      return;
    }

    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return;
    }

    try {
      Field f = TimeZone.class.getDeclaredField("defaultZoneTL");
      f.setAccessible(true);
      ThreadLocal<?> tl = (ThreadLocal<?>) f.get(null);
      Method setZone = ThreadLocal.class.getMethod("set", Object.class);
      setZone.invoke(tl, timeZone);
    } catch (Exception e) {
      try {
        Method setZone = TimeZone.class.getDeclaredMethod("setDefaultInAppContext", TimeZone.class);
        setZone.setAccessible(true);
        setZone.invoke(null, timeZone);
      } catch (Exception ex) {
        throw new RuntimeException("Unable to restore the previous TimeZone", ex);
      }
    }
  }

  @Override
  public CountDownLatch restart() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot restart a server that is not currently running.");
    }
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<CountDownLatch>() {
        @Override public CountDownLatch run() throws Exception {
          modules.shutdown();
          backendContainer.shutdownAll();
          shutdownLatch.countDown();
          modules.createConnections();
          backendContainer.configureAll(apiProxyLocal);
          modules.setApiProxyDelegate(apiProxyLocal);
          modules.startup();
          backendContainer.startupAll();
          shutdownLatch = new CountDownLatch(1);
          return shutdownLatch;
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  @Override
  public void shutdown() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot shutdown a server that is not currently running.");
    }
    try {
      AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
        @Override public Void run() throws Exception {
          modules.shutdown();
          backendContainer.shutdownAll();
          ApiProxy.setDelegate(null);
          apiProxyLocal = null;
          serverState = ServerState.SHUTDOWN;
          shutdownLatch.countDown();
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  @Override
  public void gracefulShutdown() throws IllegalStateException {

    AccessController.doPrivileged(new PrivilegedAction<Future<Void>>() {
      @Override
      public Future<Void> run() {
        return shutdownScheduler.schedule(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            shutdown();
            return null;
          }
        }, 1000, TimeUnit.MILLISECONDS);
      }
    });
  }

  @Override
  public int getPort() {
    reportDeferredConfigurationException();
    return modules.getMainModule().getMainContainer().getPort();
  }

  protected void reportDeferredConfigurationException() {
    if (configurationException != null) {
      throw new AppEngineConfigException("Invalid configuration", configurationException);
    }
  }

  @Override
  public AppContext getAppContext() {
    reportDeferredConfigurationException();
    return modules.getMainModule().getMainContainer().getAppContext();
  }

  @Override
  public AppContext getCurrentAppContext() {
    AppContext result = null;
    Environment env = ApiProxy.getCurrentEnvironment();
    if (env != null && env.getVersionId() != null) {
      String moduleName = env.getModuleId();
      result = modules.getModule(moduleName).getMainContainer().getAppContext();
    }
    return result;
  }

  @Override
  public void setThrowOnEnvironmentVariableMismatch(boolean throwOnMismatch) {
    if (configurationException == null) {
      applicationConfigurationManager.setEnvironmentVariableMismatchReportingPolicy(
          throwOnMismatch ? MismatchReportingPolicy.EXCEPTION : MismatchReportingPolicy.LOG);
    }
  }

  /**
   * We're happy with the default logging behavior, which is to
   * install a {@link ConsoleHandler} at the root level.  The only
   * issue is that we want its level to be FINEST to be consistent
   * with our runtime environment.
   *
   * <p>Note that this does not mean that any fine messages will be
   * logged by default -- each Logger still defaults to INFO.
   * However, it is sufficient to call {@link Logger#setLevel(Level)}
   * to adjust the level.
   */
  private void initializeLogging() {
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        handler.setLevel(Level.FINEST);
      }
    }
  }

  ServerState getServerState() {
    return serverState;
  }
}

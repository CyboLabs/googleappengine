// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@link ThreadFactory} creates {@link Thread} objects that
 * live independent of the current request.  This means that they
 * receive their own {@code LocalEnvironment} and have their own set
 * of request ID and {@link RequestEndListener} objects.
 *
 */
public class BackgroundThreadFactory implements ThreadFactory {
  private static final Logger logger = Logger.getLogger(BackgroundThreadFactory.class.getName());

  private static final int API_CALL_LATENCY_MS = 20;
  private static final int THREAD_STARTUP_LATENCY_MS = 20;

  private final String appId;
  private final String moduleName;
  private final String majorVersionId;

  public BackgroundThreadFactory(String appId, String moduleName, String majorVersionId) {
    this.appId = appId;
    this.moduleName = moduleName;
    this.majorVersionId = majorVersionId;
  }

  @Override
  public Thread newThread(final Runnable runnable) {
    final LocalBackgroundEnvironment environment =
        new LocalBackgroundEnvironment(appId, moduleName, majorVersionId,
            LocalEnvironment.getCurrentInstance(), LocalEnvironment.getCurrentPort());

    final boolean callerNativeMode = DevSocketImplFactory.isNativeSocketMode();

    sleepUninterruptably(API_CALL_LATENCY_MS);
    return AccessController.doPrivileged(new PrivilegedAction<Thread>() {
        @Override
        public Thread run() {
          Thread thread = new Thread(runnable) {
              @Override
              public void run() {
                sleepUninterruptably(THREAD_STARTUP_LATENCY_MS);
                DevSocketImplFactory.setSocketNativeMode(callerNativeMode);
                ApiProxy.setEnvironmentForCurrentThread(environment);
                try {
                  runnable.run();
                } finally {
                  environment.callRequestEndListeners();
                }
              }
            };
            System.setProperty("devappserver-thread-" + thread.getName(), "true");
          return thread;
        }
    });
  }

  final String getAppId() {
    return appId;
  }

  private void sleepUninterruptably(long sleepMillis) {
    try {
      Thread.sleep(sleepMillis);
    } catch (InterruptedException ex) {
      logger.log(Level.INFO, "Interrupted simulating latency:", ex);
      Thread.currentThread().interrupt();
    }
  }

  private static class LocalBackgroundEnvironment extends LocalEnvironment {
    public LocalBackgroundEnvironment(String appId, String moduleName,
        String majorVersionId, int instance, int port) {
      super(appId, moduleName, majorVersionId, instance, port, null);
    }

    @Override
    public String getEmail() {
      return null;
    }

    @Override
    public boolean isLoggedIn() {
      return false;
    }

    @Override
    public boolean isAdmin() {
      return false;
    }
  }
}

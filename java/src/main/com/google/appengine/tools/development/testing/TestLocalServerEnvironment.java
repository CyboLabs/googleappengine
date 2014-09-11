// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.tools.development.LocalServerEnvironment;

import java.io.File;

/**
 * {@link LocalServerEnvironment} implementation used for local service tests.
 *
 */
class TestLocalServerEnvironment implements LocalServerEnvironment {
  static final int TEST_PORT = 8080;

  private final boolean enforceApiDeadlines;
  private final boolean simulateProdLatencies;

  TestLocalServerEnvironment(boolean enforceApiDeadlines, boolean simulateProdLatencies) {
    this.enforceApiDeadlines = enforceApiDeadlines;
    this.simulateProdLatencies = simulateProdLatencies;
  }

  @Override
  public File getAppDir() {
    return new File(".");
  }

  @Override
  public String getAddress() {
    return "localhost";
  }

  @Override
  public String getHostName() {
    return "localhost";
  }

  @Override
  public int getPort() {
    return TEST_PORT;
  }

  @Override
  public void waitForServerToStart() {
  }

  @Override
  public boolean enforceApiDeadlines() {
    return enforceApiDeadlines;
  }

  @Override
  public boolean simulateProductionLatencies() {
    return simulateProdLatencies;
  }
}

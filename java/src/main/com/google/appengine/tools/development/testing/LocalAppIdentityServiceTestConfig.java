// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.appengine.tools.development.testing;

import com.google.appengine.api.appidentity.dev.LocalAppIdentityService;
import com.google.appengine.tools.development.ApiProxyLocal;

/**
 * Config for accessing the local app identity service in tests.
 *
 */
public class LocalAppIdentityServiceTestConfig implements LocalServiceTestConfig {

  private String defaultGcsBucketName = null;

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    if (defaultGcsBucketName != null) {
      proxy.setProperty("appengine.default.gcs.bucket.name", defaultGcsBucketName);
    }
  }

  @Override
  public void tearDown() {}

  public static LocalAppIdentityService getLocalSecretsService() {
    return (LocalAppIdentityService)
        LocalServiceTestHelper.getLocalService(LocalAppIdentityService.PACKAGE);
  }

  public LocalAppIdentityServiceTestConfig setDefaultGcsBucketName(String defaultGcsBucketName) {
    this.defaultGcsBucketName = defaultGcsBucketName;
    return this;
  }
}

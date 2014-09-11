// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import static com.google.appengine.api.datastore.dev.DefaultHighRepJobPolicy.UNAPPLIED_JOB_PERCENTAGE_PROPERTY;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.AUTO_ID_ALLOCATION_POLICY_PROPERTY;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.AutoIdAllocationPolicy.SCATTERED;
import static com.google.appengine.api.datastore.dev.LocalDatastoreService.HIGH_REP_JOB_POLICY_CLASS_PROPERTY;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;

/**
 * Manage {@link DevAppServer} local datastore service properties.
 *
 * This permits applying defaults specific to the local datastore run in the
 * {@link DevAppServer} or in {@link DevAppServer} integration tests. These
 * defaults do not apply to the datastore service unit test config.
 *
 */
public class DevAppServerDatastorePropertyHelper {

  private DevAppServerDatastorePropertyHelper() {
  }

  private static final DevAppServerDatastorePropertyHelper HELPER =
      new DevAppServerDatastorePropertyHelper();

  private class DatastoreProperty {
    private final String property;
    private final String defaultValue;

    DatastoreProperty(String property, String value) {
      this.property = property;
      this.defaultValue = value;
    }

    boolean isConfigured(Map<String, String> properties) {
      return properties.get(property) != null;
    }

    void maybeApplyDefault(Map<String, String> properties) {
      if (!isConfigured(properties)) {
        properties.put(property, defaultValue);
      }
    }
  }

  private final List<DatastoreProperty> defaultDatastoreProperties =
      new ImmutableList.Builder<DatastoreProperty>()

      .add(new DatastoreProperty(UNAPPLIED_JOB_PERCENTAGE_PROPERTY, "10") {
        @Override
        boolean isConfigured(Map<String, String> properties) {
          return properties.get(UNAPPLIED_JOB_PERCENTAGE_PROPERTY) != null ||
              properties.get(HIGH_REP_JOB_POLICY_CLASS_PROPERTY) != null;
        }
      })

      .add(new DatastoreProperty(AUTO_ID_ALLOCATION_POLICY_PROPERTY, SCATTERED.toString()))
      .build();

  /**
   * Apply DevAppServer local datastore service property defaults where
   * properties are not already otherwise configured.
   */
  public static void setDefaultProperties(Map<String, String> serviceProperties) {
    checkNotNull(serviceProperties, "serviceProperties cannot be null");
    for (DatastoreProperty property : HELPER.defaultDatastoreProperties) {
      property.maybeApplyDefault(serviceProperties);
    }
  }
}

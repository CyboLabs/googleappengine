// Copyright 2012 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

/**
 * This interface should be implemented by providers of the {@link DatastoreService} and registered
 * with {@link com.google.appengine.spi.ServiceFactoryFactory}.
 *
 */
public interface IDatastoreServiceFactory {
  /**
   * Creates a {@code DatastoreService} using the provided config.
   */
  DatastoreService getDatastoreService(DatastoreServiceConfig config);

  /**
   * Creates an {@code AsyncDatastoreService} using the provided config.
   */
  AsyncDatastoreService getAsyncDatastoreService(DatastoreServiceConfig config);

}

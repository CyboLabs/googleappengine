// Copyright 2012 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.DatastoreServiceConfig.ApiVersion;
import com.google.common.base.Preconditions;

/**
 * Creates DatastoreService instances.
 *
 */
final class DatastoreServiceFactoryImpl implements IDatastoreServiceFactory {

  @Override
  public DatastoreService getDatastoreService(DatastoreServiceConfig config) {
    return new DatastoreServiceImpl(getAsyncDatastoreService(config));
  }

  @Override
  public AsyncDatastoreServiceInternal getAsyncDatastoreService(DatastoreServiceConfig config) {
    TransactionStack txnStack = new TransactionStackImpl();
    DatastoreV4Proxy datastoreProxy = config.getDatastoreV4Proxy();

    ApiVersion apiVersion = config.getApiVersion();
    switch (apiVersion) {
      case V3:
        Preconditions.checkState(datastoreProxy == null);
        return new AsyncDatastoreServiceImpl(
            config,
            config.constructApiConfig(),
            txnStack);
      case V4:
        Preconditions.checkState(datastoreProxy == null);
        return new AsyncDatastoreV4ServiceImpl(
            config,
            new AppEngineDatastoreV4Proxy(config.constructApiConfig()),
            txnStack);
      case CLOUD_DATASTORE:
        return new AsyncDatastoreV4ServiceImpl(
            config,
            datastoreProxy,
            txnStack);
      default:
        throw new IllegalArgumentException("Can't instantiate service with version: " + apiVersion);
    }
  }
}

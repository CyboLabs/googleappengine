package com.google.appengine.api.datastore;

import java.util.concurrent.Future;

/**
 * Internal interface to allow type compatibility between V3 and V4 service
 * providers, i.e. {@link AsyncDatastoreServiceImpl}. Unsupported methods throw
 * {@link UnsupportedOperationException}.
 */
interface AsyncDatastoreServiceInternal extends AsyncDatastoreService {

  /**
   * @see DatastoreService#allocateIdRange(KeyRange)
   */
  Future<DatastoreService.KeyRangeState> allocateIdRange(final KeyRange range);
}
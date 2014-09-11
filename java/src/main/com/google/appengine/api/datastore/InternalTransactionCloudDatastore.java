package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.Mutation;

import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Stores transaction state as java proto objects, with the expectation that the
 * {@link DatastoreV4Proxy} will need to inspect a deserialized version of the commit.
 */
class InternalTransactionCloudDatastore extends BaseInternalTransactionV4<Mutation> {
  /**
   * Objects should be created with {@link #create(DatastoreV4Proxy, Future)} due
   * to post-construction manipulation.
   */
  private InternalTransactionCloudDatastore(DatastoreV4Proxy dsApiProxy,
      Future<BeginTransactionResponse> beginTxnFuture) {
    super(dsApiProxy, beginTxnFuture);
  }

  static TransactionImpl.InternalTransaction create(DatastoreV4Proxy dsApiProxy,
      Future<BeginTransactionResponse> future) {
    return registerTxn(new InternalTransactionCloudDatastore(dsApiProxy, future));
  }

  @Override
  Mutation serializeMutation(Mutation mutation) {
    return mutation;
  }

  @Override
  Future<?> sendCommit(Collection<Mutation> mutations) {
    return dsApiProxy.commit(CommitRequest.newBuilder()
        .setTransaction(getHandle())
        .addAllMutation(mutations)
        .build());
  }
}

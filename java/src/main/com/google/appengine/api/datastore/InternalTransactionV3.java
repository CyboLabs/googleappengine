// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiBasePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.CommitResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.io.protocol.ProtocolMessage;

import java.util.concurrent.Future;

/**
 * Implementation of the V3-specific logic to handle a {@link Transaction}.
 *
 * All calls are routed through the {@link ApiProxy}.
 */
class InternalTransactionV3 implements TransactionImpl.InternalTransaction {

  private final ApiConfig apiConfig;
  private final String app;
  /**
   * The {@link Future} associated with the BeginTransaction RPC we sent to the
   * datastore server.
   */
  private final Future<DatastoreV3Pb.Transaction> beginTxnFuture;

  InternalTransactionV3(ApiConfig apiConfig, String app,
      Future<DatastoreV3Pb.Transaction> beginTxnFuture) {
    this.apiConfig = apiConfig;
    this.app = app;
    this.beginTxnFuture = beginTxnFuture;
  }

  /**
   * Provides the unique identifier for the txn.
   * Blocks on the future since the handle comes back from the datastore
   * server.
   */
  private long getHandle() {
    return FutureHelper.quietGet(beginTxnFuture).getHandle();
  }

  <T extends ProtocolMessage<T>> Future<Void> makeAsyncCall(
      Method method, ProtocolMessage<?> request, T response) {
    Future<T> resultProto = DatastoreApiHelper.makeAsyncCall(apiConfig, method, request, response);
    return new FutureWrapper<T, Void>(resultProto) {
      @Override
      protected Void wrap(T ignore) throws Exception {
        return null;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  private <T extends ProtocolMessage<T>> Future<Void> makeAsyncTxnCall(Method method, T response) {
    DatastoreV3Pb.Transaction txn = new DatastoreV3Pb.Transaction();
    txn.setApp(app);
    txn.setHandle(getHandle());
    return makeAsyncCall(method, txn, response);
  }

  @Override
  public Future<Void> doCommitAsync() {
    return makeAsyncTxnCall(Method.Commit, new CommitResponse());
  }

  @Override
  public Future<Void> doRollbackAsync() {
    return makeAsyncTxnCall(Method.Rollback, new ApiBasePb.VoidProto());
  }

  @Override
  public String getId() {
    return Long.toString(getHandle());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    InternalTransactionV3 that = (InternalTransactionV3) o;

    return getHandle() == that.getHandle();
  }

  @Override
  public int hashCode() {
    return (int) (getHandle() ^ (getHandle() >>> 32));
  }

  static DatastoreV3Pb.Transaction localTxnToRemoteTxn(Transaction local) {
    DatastoreV3Pb.Transaction remote = new DatastoreV3Pb.Transaction();
    remote.setApp(local.getApp());
    remote.setHandle(Long.parseLong(local.getId()));
    return remote;
  }
}

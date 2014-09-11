package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV4.AllocateIdsRequest;
import com.google.apphosting.datastore.DatastoreV4.AllocateIdsResponse;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionRequest;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.CommitResponse;
import com.google.apphosting.datastore.DatastoreV4.ContinueQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.ContinueQueryResponse;
import com.google.apphosting.datastore.DatastoreV4.LookupRequest;
import com.google.apphosting.datastore.DatastoreV4.LookupResponse;
import com.google.apphosting.datastore.DatastoreV4.RollbackRequest;
import com.google.apphosting.datastore.DatastoreV4.RollbackResponse;
import com.google.apphosting.datastore.DatastoreV4.RunQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.RunQueryResponse;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.concurrent.Future;

/**
 * The V4 RPC interface. The default implementation, {@link AppEngineDatastoreV4Proxy}, forwards
 * RPCs to the App Engine general RPC proxy. Other implementations may handle RPCs differently.
 *
 * The use of an interface allows the SDKs to be used in contexts other than the App Engine
 * runtime, without introducing a direct build dependence.
 *
 * Invoking a method sends out the supplied RPC and returns a Future which clients can
 * block on to retrieve a result.
 */
interface DatastoreV4Proxy {
  Future<BeginTransactionResponse> beginTransaction(BeginTransactionRequest request);
  Future<RollbackResponse> rollback(RollbackRequest request);
  Future<RunQueryResponse> runQuery(RunQueryRequest request);
  Future<ContinueQueryResponse> continueQuery(ContinueQueryRequest request);
  Future<LookupResponse> lookup(LookupRequest request);
  Future<AllocateIdsResponse> allocateIds(AllocateIdsRequest request);
  Future<CommitResponse> commit(CommitRequest request);

  /**
   * Equivalent to {@link #commit(CommitRequest)} with a pre-serialized proto.
   * Used by {@link InternalTransactionV4} to avoid a second serialization of the proto.
   *
   * @param request byte array which must be deserializable as a {@link CommitRequest}.
   */
  Future<CommitResponse> rawCommit(byte[] request) throws InvalidProtocolBufferException;
}
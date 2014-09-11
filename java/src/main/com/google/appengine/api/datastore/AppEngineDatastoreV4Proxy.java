package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DatastoreApiHelper.makeAsyncCall;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV4.AllocateIdsRequest;
import com.google.apphosting.datastore.DatastoreV4.AllocateIdsResponse;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionRequest;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.CommitResponse;
import com.google.apphosting.datastore.DatastoreV4.ContinueQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.ContinueQueryResponse;
import com.google.apphosting.datastore.DatastoreV4.DatastoreV4Service.Method;
import com.google.apphosting.datastore.DatastoreV4.LookupRequest;
import com.google.apphosting.datastore.DatastoreV4.LookupResponse;
import com.google.apphosting.datastore.DatastoreV4.RollbackRequest;
import com.google.apphosting.datastore.DatastoreV4.RollbackResponse;
import com.google.apphosting.datastore.DatastoreV4.RunQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.RunQueryResponse;

import java.util.concurrent.Future;

/**
 * V4 proxy which forwards RPCs to {@link ApiProxy} via {@link DatastoreApiHelper}.
 * Used when running in an App Engine environment.
 */
final class AppEngineDatastoreV4Proxy implements DatastoreV4Proxy {
  private final ApiConfig apiConfig;

  AppEngineDatastoreV4Proxy(ApiConfig apiConfig) {
    this.apiConfig = apiConfig;
  }

  @Override
  public Future<BeginTransactionResponse> beginTransaction(BeginTransactionRequest request) {
    return makeAsyncCall(apiConfig, Method.BeginTransaction, request,
        BeginTransactionResponse.PARSER);
  }

  @Override
  public Future<RollbackResponse> rollback(RollbackRequest request) {
    return makeAsyncCall(apiConfig, Method.Rollback, request, RollbackResponse.PARSER);
  }

  @Override
  public Future<RunQueryResponse> runQuery(RunQueryRequest request) {
    return makeAsyncCall(apiConfig, Method.RunQuery, request, RunQueryResponse.PARSER);
  }

  @Override
  public Future<ContinueQueryResponse> continueQuery(ContinueQueryRequest request) {
    return makeAsyncCall(apiConfig, Method.ContinueQuery, request, ContinueQueryResponse.PARSER);
  }

  @Override
  public Future<LookupResponse> lookup(LookupRequest request) {
    return makeAsyncCall(apiConfig, Method.Lookup, request, LookupResponse.PARSER);
  }

  @Override
  public Future<AllocateIdsResponse> allocateIds(AllocateIdsRequest request) {
    return makeAsyncCall(apiConfig, Method.AllocateIds, request, AllocateIdsResponse.PARSER);
  }

  @Override
  public Future<CommitResponse> commit(CommitRequest request) {
    return makeAsyncCall(apiConfig, Method.Commit, request, CommitResponse.PARSER);
  }

  @Override
  public Future<CommitResponse> rawCommit(byte[] request) {
    return makeAsyncCall(apiConfig, Method.Commit, request, CommitResponse.PARSER);
  }
}

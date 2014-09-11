package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV4.ContinueQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.ContinueQueryResponse;
import com.google.apphosting.datastore.DatastoreV4.RunQueryResponse;

import java.util.concurrent.Future;

/**
 * V4 service specific code for iterating query results and requesting more results.
 * Instances can be shared between queries using the same ApiConfig.
 */
class QueryResultsSourceV4 extends
    BaseQueryResultsSource<RunQueryResponse, ContinueQueryRequest, ContinueQueryResponse> {

  private final DatastoreV4Proxy datastoreProxy;

  QueryResultsSourceV4(DatastoreCallbacks callbacks, FetchOptions fetchOptions, Transaction txn,
      Query query, Future<RunQueryResponse> runQueryResponse, DatastoreV4Proxy datastoreProxy) {
    super(callbacks, fetchOptions, txn, query, runQueryResponse);
    this.datastoreProxy = datastoreProxy;
  }

  @Override
  ContinueQueryRequest buildNextCallPrototype(RunQueryResponse initialResponse) {
    return ContinueQueryRequest.newBuilder()
        .setQueryHandle(initialResponse.getQueryHandle())
        .build();
  }

  @Override
  Future<ContinueQueryResponse> makeNextCall(ContinueQueryRequest prototype,
      WrappedQueryResult queryResult, Integer fetchCountOrNull, Integer offsetOrNull) {
    return datastoreProxy.continueQuery(prototype);
  }

  @Override
  WrappedQueryResult wrapInitialResult(RunQueryResponse initialResponse) {
    return new WrappedQueryResultV4(initialResponse.getBatch());
  }

  @Override
  WrappedQueryResult wrapResult(ContinueQueryResponse res) {
    return new WrappedQueryResultV4(res.getBatch());
  }
}

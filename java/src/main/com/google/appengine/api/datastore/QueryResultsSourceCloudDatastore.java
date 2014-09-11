package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV4;
import com.google.apphosting.datastore.DatastoreV4.QueryResultBatch;
import com.google.apphosting.datastore.DatastoreV4.RunQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.RunQueryResponse;

import java.util.concurrent.Future;

class QueryResultsSourceCloudDatastore extends
    BaseQueryResultsSource<RunQueryResponse, RunQueryRequest, RunQueryResponse> {

  private final DatastoreV4Proxy dsApiProxy;
  private final RunQueryRequest initialRequest;
  private int remainingLimit;

  QueryResultsSourceCloudDatastore(DatastoreCallbacks callbacks, FetchOptions fetchOptions,
      Transaction txn, Query query, RunQueryRequest request,
      Future<RunQueryResponse> runQueryResponse, DatastoreV4Proxy dsApiProxy) {
    super(callbacks, fetchOptions, txn, query, runQueryResponse);
    this.initialRequest = request;
    this.dsApiProxy = dsApiProxy;
    remainingLimit = fetchOptions.getLimit() != null ? fetchOptions.getLimit() : -1;
  }

  @Override
  RunQueryRequest buildNextCallPrototype(RunQueryResponse initialResponse) {
    return initialRequest;
  }

  @Override
  Future<RunQueryResponse> makeNextCall(RunQueryRequest prototype, WrappedQueryResult latestResult,Integer fetchCount,Integer offset) {
    RunQueryRequest.Builder runQueryRequest = prototype.toBuilder();
    DatastoreV4.Query.Builder query = runQueryRequest.getQueryBuilder();
    QueryResultBatch latestBatch = ((WrappedQueryResultV4) latestResult).getBatch();
    if (!latestBatch.hasEndCursor()) {
      throw new IllegalArgumentException();
    }
    query.setStartCursor(latestBatch.getEndCursor());
    if (query.hasLimit()) {
      remainingLimit -= latestBatch.getEntityResultCount();
      query.setLimit(Math.max(remainingLimit, 0));
    }
    if (offset != null) {
      query.setOffset(offset);
    } else {
      query.clearOffset();
    }
    return dsApiProxy.runQuery(runQueryRequest.build());
  }

  @Override
  WrappedQueryResult wrapInitialResult(RunQueryResponse initialResponse) {
    return new WrappedQueryResultV4(initialResponse.getBatch());
  }

  @Override
  WrappedQueryResult wrapResult(RunQueryResponse res) {
    return new WrappedQueryResultV4(res.getBatch());
  }
}

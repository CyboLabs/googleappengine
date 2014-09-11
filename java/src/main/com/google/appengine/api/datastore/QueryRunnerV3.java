package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.CompositeIndexManager.IndexComponentsOnlyQuery;
import com.google.appengine.api.datastore.CompositeIndexManager.IndexSource;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.storage.onestore.v3.OnestoreEntity;

import java.util.concurrent.Future;

/**
 * V3 service specific code for constructing and sending queries.
 * This class is threadsafe and has no state.
 */
final class QueryRunnerV3 implements QueryRunner {

  private final DatastoreServiceConfig datastoreServiceConfig;
  private final ApiConfig apiConfig;

  QueryRunnerV3(DatastoreServiceConfig datastoreServiceConfig, ApiConfig apiConfig) {
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.apiConfig = apiConfig;
  }

  @Override
  public QueryResultsSource runQuery(FetchOptions fetchOptions, Query query, Transaction txn) {
    final DatastoreV3Pb.Query queryProto = convertToPb(query, txn, fetchOptions);
    if (datastoreServiceConfig.getReadPolicy().getConsistency() == Consistency.EVENTUAL) {
      queryProto.setFailoverMs(BaseAsyncDatastoreServiceImpl.ARBITRARY_FAILOVER_READ_MS);
      queryProto.setStrong(false);
    }

    Future<DatastoreV3Pb.QueryResult> result = DatastoreApiHelper.makeAsyncCall(
        apiConfig, Method.RunQuery, queryProto, new DatastoreV3Pb.QueryResult());

    result = new FutureWrapper<DatastoreV3Pb.QueryResult, DatastoreV3Pb.QueryResult>(result) {
      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof DatastoreNeedIndexException) {
          addMissingIndexData(queryProto, (DatastoreNeedIndexException) cause);
        }
        return cause;
      }

      @Override
      protected DatastoreV3Pb.QueryResult wrap(DatastoreV3Pb.QueryResult result) throws Exception {
        return result;
      }
    };

    return new QueryResultsSourceV3(datastoreServiceConfig.getDatastoreCallbacks(),
        fetchOptions, txn, query, result, apiConfig);
  }

  private void addMissingIndexData(
      DatastoreV3Pb.Query queryProto, DatastoreNeedIndexException e) {
    IndexComponentsOnlyQuery indexQuery = new IndexComponentsOnlyQuery(queryProto);
    CompositeIndexManager mgr = new CompositeIndexManager();
    OnestoreEntity.Index index = mgr.compositeIndexForQuery(indexQuery);
    if (index != null) {
      String xml = mgr.generateXmlForIndex(index, IndexSource.manual);
      e.setMissingIndexDefinitionXml(xml);
    } else {
    }
  }

  private DatastoreV3Pb.Query convertToPb(Query q, Transaction txn, FetchOptions fetchOptions) {
    DatastoreV3Pb.Query queryProto = QueryTranslator.convertToPb(q, fetchOptions);
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      queryProto.setTransaction(InternalTransactionV3.localTxnToRemoteTxn(txn));
    }
    return queryProto;
  }
}

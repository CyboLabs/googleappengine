package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.DatastoreServiceConfig.ApiVersion;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.apphosting.datastore.DatastoreV4;
import com.google.apphosting.datastore.DatastoreV4.CompositeFilter;
import com.google.apphosting.datastore.DatastoreV4.PropertyExpression;
import com.google.apphosting.datastore.DatastoreV4.PropertyFilter;
import com.google.apphosting.datastore.DatastoreV4.PropertyOrder;
import com.google.apphosting.datastore.DatastoreV4.PropertyReference;
import com.google.apphosting.datastore.DatastoreV4.ReadOptions.ReadConsistency;
import com.google.apphosting.datastore.DatastoreV4.RunQueryRequest;
import com.google.apphosting.datastore.DatastoreV4.RunQueryResponse;
import com.google.apphosting.datastore.EntityV4;
import com.google.apphosting.datastore.EntityV4.PartitionId;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * V4 service specific code for constructing and sending queries.
 * This class is threadsafe and has no state.
 */
final class QueryRunnerV4 implements QueryRunner {

  private final DatastoreServiceConfig datastoreServiceConfig;
  private final DatastoreV4Proxy datastoreProxy;

  QueryRunnerV4(DatastoreServiceConfig datastoreServiceConfig, DatastoreV4Proxy datastoreProxy) {
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.datastoreProxy = datastoreProxy;
  }

  @Override
  public QueryResultsSource runQuery(FetchOptions fetchOptions, Query query, Transaction txn) {

    RunQueryRequest.Builder queryBldr = toV4Query(query, fetchOptions);
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      queryBldr.getReadOptionsBuilder()
          .setTransaction(InternalTransactionV4.getById(txn.getId()).getHandle());
    } else if (datastoreServiceConfig.getReadPolicy().getConsistency() == Consistency.EVENTUAL) {
      queryBldr.getReadOptionsBuilder().setReadConsistency(ReadConsistency.EVENTUAL);
    }

    RunQueryRequest request = queryBldr.build();
    Future<RunQueryResponse> result = datastoreProxy.runQuery(request);

    if (datastoreServiceConfig.getApiVersion() == ApiVersion.CLOUD_DATASTORE) {
      return new QueryResultsSourceCloudDatastore(datastoreServiceConfig.getDatastoreCallbacks(),
          fetchOptions, txn, query, request, result, datastoreProxy);
    } else {
      return new QueryResultsSourceV4(datastoreServiceConfig.getDatastoreCallbacks(),
          fetchOptions, txn, query, result, datastoreProxy);
    }
  }

  static RunQueryRequest.Builder toV4Query(Query query, FetchOptions fetchOptions) {

    Preconditions.checkArgument(query.getFullTextSearch() == null, "full-text search unsupported");

    Preconditions.checkArgument(query.getFilter() == null);

    RunQueryRequest.Builder requestBldr = RunQueryRequest.newBuilder();

    if (fetchOptions.getChunkSize() != null) {
      requestBldr.setSuggestedBatchSize(fetchOptions.getChunkSize());
    } else if (fetchOptions.getPrefetchSize() != null) {
      requestBldr.setSuggestedBatchSize(fetchOptions.getPrefetchSize());
    }

    PartitionId.Builder partitionId = requestBldr.getPartitionIdBuilder()
        .setDatasetId(query.getAppId());
    if (!query.getNamespace().isEmpty()) {
      partitionId.setNamespace(query.getNamespace());
    }

    DatastoreV4.Query.Builder queryBldr = requestBldr.getQueryBuilder();

    if (query.getKind() != null) {
      queryBldr.addKindBuilder().setName(query.getKind());
    }

    if (fetchOptions.getOffset() != null) {
      queryBldr.setOffset(fetchOptions.getOffset());
    }

    if (fetchOptions.getLimit() != null) {
      queryBldr.setLimit(fetchOptions.getLimit());
    }

    if (fetchOptions.getStartCursor() != null) {
      queryBldr.setStartCursor(fetchOptions.getStartCursor().convertToPb().toByteString());
    }

    if (fetchOptions.getEndCursor() != null) {
      queryBldr.setEndCursor(fetchOptions.getEndCursor().convertToPb().toByteString());
    }

    Set<String> groupByProperties = Sets.newHashSet();
    if (query.getDistinct()) {
      if (query.getProjections().isEmpty()) {
        throw new IllegalArgumentException(
            "Projected properties must be set to allow for distinct projections");
      }
      for (Projection projection : query.getProjections()) {
        String name = projection.getPropertyName();
        groupByProperties.add(name);
        queryBldr.addGroupByBuilder().setName(name);
      }
    }

    for (Projection projection : query.getProjections()) {
      String name = projection.getPropertyName();
      PropertyExpression.Builder projBuilder = queryBldr.addProjectionBuilder();
      projBuilder.getPropertyBuilder().setName(name);
      if (!groupByProperties.isEmpty() && !groupByProperties.contains(name)) {
        projBuilder.setAggregationFunction(PropertyExpression.AggregationFunction.FIRST);
      }
    }

    if (query.isKeysOnly()) {
      PropertyExpression.Builder projBuilder = queryBldr.addProjectionBuilder();
      projBuilder.getPropertyBuilder().setName(Entity.KEY_RESERVED_PROPERTY);
      if (!groupByProperties.isEmpty()
          && !groupByProperties.contains(Entity.KEY_RESERVED_PROPERTY)) {
        projBuilder.setAggregationFunction(PropertyExpression.AggregationFunction.FIRST);
      }
    }

    CompositeFilter.Builder compositeFilter = CompositeFilter.newBuilder();
    if (query.getAncestor() != null) {
      compositeFilter.addFilterBuilder().getPropertyFilterBuilder()
          .setOperator(PropertyFilter.Operator.HAS_ANCESTOR)
          .setProperty(PropertyReference.newBuilder().setName(Entity.KEY_RESERVED_PROPERTY))
          .setValue(EntityV4.Value.newBuilder()
              .setKeyValue(DataTypeTranslator.toV4Key(query.getAncestor())));
    }
    for (Query.FilterPredicate filterPredicate : query.getFilterPredicates()) {
      compositeFilter.addFilterBuilder().setPropertyFilter(toV4PropertyFilter(filterPredicate));
    }
    if (compositeFilter.getFilterCount() == 1) {
      queryBldr.setFilter(compositeFilter.getFilter(0));
    } else if (compositeFilter.getFilterCount() > 1) {
      queryBldr.getFilterBuilder()
          .setCompositeFilter(compositeFilter.setOperator(CompositeFilter.Operator.AND));
    }

    for (Query.SortPredicate sortPredicate : query.getSortPredicates()) {
      queryBldr.addOrder(toV4PropertyOrder(sortPredicate));
    }

    return requestBldr;
  }

  private static PropertyFilter.Builder toV4PropertyFilter(Query.FilterPredicate predicate) {
    PropertyFilter.Builder filter = PropertyFilter.newBuilder();
    FilterOperator operator = predicate.getOperator();
    Object value = predicate.getValue();
    if (operator == Query.FilterOperator.IN) {
      if (!(predicate.getValue() instanceof Collection<?>)) {
        throw new IllegalArgumentException("IN filter value is not a Collection.");
      }
      Collection<?> valueCollection = (Collection<?>) value;
      if (valueCollection.size() != 1) {
        throw new IllegalArgumentException("This service only supports 1 object for IN.");
      }
      operator = Query.FilterOperator.EQUAL;
      value = valueCollection.iterator().next();
    }
    filter.setOperator(toV4PropertyFilterOperator(operator));
    filter.getPropertyBuilder().setName(predicate.getPropertyName());
    filter.setValue(DataTypeTranslator.toV4Value(value, true));

    return filter;
  }

  private static PropertyFilter.Operator toV4PropertyFilterOperator(FilterOperator operator) {
    switch (operator) {
      case LESS_THAN:
        return PropertyFilter.Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return PropertyFilter.Operator.LESS_THAN_OR_EQUAL;
      case GREATER_THAN:
        return PropertyFilter.Operator.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL:
        return PropertyFilter.Operator.GREATER_THAN_OR_EQUAL;
      case EQUAL:
        return PropertyFilter.Operator.EQUAL;
      default:
        throw new IllegalArgumentException("Can't convert: " + operator);
    }
  }

  private static PropertyOrder.Builder toV4PropertyOrder(Query.SortPredicate predicate) {
    return PropertyOrder.newBuilder()
        .setProperty(PropertyReference.newBuilder().setName(predicate.getPropertyName()))
        .setDirection(toV4PropertyOrderDirection(predicate.getDirection()));
  }

  private static PropertyOrder.Direction toV4PropertyOrderDirection(Query.SortDirection direction) {
    switch (direction) {
      case ASCENDING:
        return PropertyOrder.Direction.ASCENDING;
      case DESCENDING:
        return PropertyOrder.Direction.DESCENDING;
      default:
        throw new IllegalArgumentException("direction: " + direction);
    }
  }

}

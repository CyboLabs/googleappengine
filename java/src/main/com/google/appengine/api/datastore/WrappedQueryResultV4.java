package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.BaseQueryResultsSource.WrappedQueryResult;
import com.google.apphosting.datastore.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore.DatastoreV4;
import com.google.apphosting.datastore.DatastoreV4.QueryResultBatch;
import com.google.apphosting.datastore.DatastoreV4.QueryResultBatch.MoreResultsType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for V4 protos with common functions.
 */
class WrappedQueryResultV4 implements WrappedQueryResult {
  private final QueryResultBatch batch;

  WrappedQueryResultV4(QueryResultBatch batch) {
    this.batch = batch;
  }

  @Override
  public Cursor getEndCursor() {
    if (batch.hasEndCursor()) {
      try {
        return new Cursor(CompiledCursor.PARSER.parseFrom(batch.getEndCursor()));
      } catch (InvalidProtocolBufferException e) {
        throw new RuntimeException("Can't parse cursor", e);
      }
    }
    return null;
  }

  @Override
  public List<Entity> getEntities(Collection<Projection> projections) {
    List<Entity> entityList = Lists.newArrayListWithCapacity(batch.getEntityResultCount());
    if (projections.isEmpty()) {
      for (DatastoreV4.EntityResult entityResult : batch.getEntityResultList()) {
        entityList.add(DataTypeTranslator.toEntity(entityResult.getEntity()));
      }
    } else {
      for (DatastoreV4.EntityResult entityResult : batch.getEntityResultList()) {
        entityList.add(DataTypeTranslator.toEntity(entityResult.getEntity(), projections));
      }
    }
    return entityList;
  }

  @Override
  public List<Cursor> getResultCursors() {
    return Collections.<Cursor>nCopies(batch.getEntityResultCount(), null);
  }

  @Override
  public Cursor getSkippedResultsCursor() {
    return null;
  }

  @Override
  public boolean hasMoreResults() {
    return batch.getMoreResults() == MoreResultsType.NOT_FINISHED;
  }

  @Override
  public int numSkippedResults() {
    return batch.getSkippedResults();
  }

  @Override
  public List<Index> getIndexInfo(Collection<Index> monitoredIndexBuffer) {
    return ImmutableList.of();
  }

  QueryResultBatch getBatch() {
    return batch;
  }
}

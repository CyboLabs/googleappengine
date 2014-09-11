// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;
import java.util.List;

/**
 * Implements PreparedQuery.
 *
 */
class PreparedQueryImpl extends BasePreparedQuery {
  private final Query query;
  private final Transaction txn;
  private final QueryRunner queryRunner;

  public PreparedQueryImpl(Query query, Transaction txn, QueryRunner queryRunner) {
    this.query = query;
    this.txn = txn;
    this.queryRunner = queryRunner;

    checkArgument(query.getFilter() == null);
    checkArgument(txn == null || query.getAncestor() != null,
          "Only ancestor queries are allowed inside transactions.");
    TransactionImpl.ensureTxnActive(txn);
  }

  private QueryResultIteratorImpl runQuery(FetchOptions fetchOptions) {
    return new QueryResultIteratorImpl(this, queryRunner.runQuery(fetchOptions, query, txn),
        fetchOptions, txn);
  }

  @Override
  public List<Entity> asList(FetchOptions fetchOptions) {
    return new LazyList(runQuery(fetchOptions));
  }

  @Override
  public QueryResultList<Entity> asQueryResultList(FetchOptions fetchOptions) {
    FetchOptions override = new FetchOptions(fetchOptions);
    if (override.getCompile() == null) {
      override.compile(true);
    }
    LazyList lazyList = new LazyList(runQuery(override));
    return lazyList;
  }

  @Override
  public Iterator<Entity> asIterator(FetchOptions fetchOptions) {
    return runQuery(fetchOptions);
  }

  @Override
  public QueryResultIterator<Entity> asQueryResultIterator(FetchOptions fetchOptions) {
    if (fetchOptions.getCompile() == null) {
      fetchOptions = new FetchOptions(fetchOptions).compile(true);
    }
    return runQuery(fetchOptions);
  }

  @Override
  public Entity asSingleEntity() throws TooManyResultsException {
    List<Entity> entities = asList(withLimit(2));
    if (entities.isEmpty()) {
      return null;
    } else if (entities.size() != 1) {
      throw new TooManyResultsException();
    }
    return entities.get(0);
  }

  @Override
  /**
   * Counts the number of entities in the result set.
   *
   * This method will run a query that will offset past all results and return
   * the number of results that have been skipped in the process.
   *
   * (offset, limit) is converted to (offset + limit, 0) so that no results are
   * actually returned. The resulting count is max(0, skipped - offset). This
   * is the number of entities in the range [offset, offset + limit) which is
   * the count.
   */
  public int countEntities(FetchOptions fetchOptions) {
    FetchOptions overrideOptions = new FetchOptions(fetchOptions);

    overrideOptions.limit(0);
    if (fetchOptions.getLimit() != null) {
      if (fetchOptions.getOffset() != null) {
        int offset = fetchOptions.getLimit() + fetchOptions.getOffset();
        overrideOptions.offset(offset >= 0 ? offset : Integer.MAX_VALUE);
      } else {
        overrideOptions.offset(fetchOptions.getLimit());
      }
    } else {
      overrideOptions.offset(Integer.MAX_VALUE);
    }

    int count = runQuery(overrideOptions).getNumSkipped();
    if (fetchOptions.getOffset() != null) {
      if (count < fetchOptions.getOffset()) {
        count = 0;
      } else {
        count = count - fetchOptions.getOffset();
      }
    }
    return count;
  }

  @Override
  public String toString() {
    return query + (txn != null ? " IN " + txn : "");
  }
}

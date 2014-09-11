package com.google.appengine.api.labs.datastore.overlay;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.datastore.QueryResultList;

import java.util.Iterator;
import java.util.List;

/**
 * An implementation of {@link PreparedQuery} for namespace-pinned queries.
 */
final class NamespacePinnedPreparedQueryImpl implements PreparedQuery {
  private final NamespacePinnedBaseDatastoreServiceImpl datastore;
  private final PreparedQuery preparedQuery;

  /**
   * Constructs a namespace-pinned {@link PreparedQuery}.
   *
   * @param datastore the namespace-pinned Datastore
   * @param preparedQuery the underlying {@link PreparedQuery}
   */
  public NamespacePinnedPreparedQueryImpl(NamespacePinnedBaseDatastoreServiceImpl datastore,
      PreparedQuery preparedQuery) {
    this.datastore = checkNotNull(datastore);
    this.preparedQuery = checkNotNull(preparedQuery);
  }

  @Override
  public List<Entity> asList(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return asQueryResultList(fetchOptions);
  }

  @Override
  public QueryResultList<Entity> asQueryResultList(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return new LazyList(runQuery(fetchOptions));
  }

  @Override
  public Iterable<Entity> asIterable(final FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return new Iterable<Entity>() {
      @Override
      public Iterator<Entity> iterator() {
        return asIterator(fetchOptions);
      }
    };
  }

  @Override
  public QueryResultIterable<Entity> asQueryResultIterable(final FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return new QueryResultIterable<Entity>() {
      @Override
      public QueryResultIterator<Entity> iterator() {
        return asQueryResultIterator(fetchOptions);
      }
    };
  }

  @Override
  public Iterable<Entity> asIterable() {
    return asIterable(withDefaults());
  }

  @Override
  public QueryResultIterable<Entity> asQueryResultIterable() {
    return asQueryResultIterable(withDefaults());
  }

  @Override
  public Iterator<Entity> asIterator(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return asQueryResultIterator(fetchOptions);
  }

  @Override
  public Iterator<Entity> asIterator() {
    return asIterator(withDefaults());
  }

  @Override
  public QueryResultIterator<Entity> asQueryResultIterator(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return runQuery(fetchOptions);
  }

  @Override
  public QueryResultIterator<Entity> asQueryResultIterator() {
    return asQueryResultIterator(withDefaults());
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
  public int countEntities(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    return preparedQuery.countEntities(fetchOptions);
  }

  @Deprecated
  @Override
  public int countEntities() {
    return preparedQuery.countEntities();
  }

  private NamespacePinnedQueryResultIteratorImpl runQuery(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    QueryResultIterator<Entity> iterator = preparedQuery.asQueryResultIterator(fetchOptions);
    return new NamespacePinnedQueryResultIteratorImpl(datastore, iterator);
  }
}

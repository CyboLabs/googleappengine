package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * An implementation of {@link QueryResultIterator<Entity>} for use with a namespace-pinned
 * {@link DatastoreService}.
 *
 * <p>We run the query as usual, and then transform any returned entities back to the original
 * namespace.
 */
final class NamespacePinnedQueryResultIteratorImpl implements QueryResultBatchIterator<Entity> {
  private final NamespacePinnedBaseDatastoreServiceImpl datastore;
  private final QueryResultIterator<Entity> iterator;

  /**
   * Constructs a namespace-pinned {@link QueryResultIterator<Entity>}.
   *
   * @param datastore the namespace-pinned Datastore
   * @param iterator the underlying iterator
   */
  public NamespacePinnedQueryResultIteratorImpl(NamespacePinnedBaseDatastoreServiceImpl datastore,
      QueryResultIterator<Entity> iterator) {
    this.datastore = checkNotNull(datastore);
    this.iterator = checkNotNull(iterator);
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public Entity next() {
    return datastore.getOriginalNamespaceEntity(iterator.next());
  }

  @Override
  public List<Entity> nextList(int maximumElements) {
    ImmutableList.Builder<Entity> builder = ImmutableList.<Entity>builder();
    for (int i = 0; i < maximumElements; i++) {
      if (!hasNext()) {
        break;
      }
      builder.add(datastore.getOriginalNamespaceEntity(next()));
    }
    return builder.build();
  }

  @Override
  public List<Index> getIndexList() {
    return iterator.getIndexList();
  }

  @Override
  public Cursor getCursor() {
    return iterator.getCursor();
  }

  @Override
  public void remove() {
    iterator.remove();
  }
}

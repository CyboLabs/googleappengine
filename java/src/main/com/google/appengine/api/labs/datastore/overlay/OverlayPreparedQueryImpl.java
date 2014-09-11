package com.google.appengine.api.labs.datastore.overlay;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityProtoComparators.EntityProtoComparator;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.datastore.Transaction;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Queues;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * An implementation of {@link PreparedQuery} for overlay queries.
 */
final class OverlayPreparedQueryImpl implements PreparedQuery {

  private static final int COUNT_ENTITIES_LEGACY_LIMIT = 1000;

  private final OverlayBaseDatastoreServiceImpl overlay;
  private final PreparedQuery preparedOverlayQuery;
  private final PreparedQuery preparedParentQuery;
  private final EntityComparator entityComparator;
  private final Transaction txn;

  /**
   * Constructs an overlay-based {@link PreparedQuery}.
   *
   * @param overlay the {@link OverlayBaseDatastoreServiceImpl}
   * @param preparedOverlayQuery the {@link PreparedQuery} on the overlay's backing Datastore
   * @param preparedParentQuery the {@link PreparedQuery} on the parent Datastore
   * @param txn the current transaction, if any
   *
   * @throws IllegalArgumentException if this multi-query required in memory sorting and the base
   *         query is both a keys-only query and sorted by anything other than its key.
   */
  public OverlayPreparedQueryImpl(OverlayBaseDatastoreServiceImpl overlay, Query query,
      PreparedQuery preparedOverlayQuery, PreparedQuery preparedParentQuery, Transaction txn) {
    checkNotNull(query);
    this.overlay = checkNotNull(overlay);
    this.preparedOverlayQuery = checkNotNull(preparedOverlayQuery);
    this.preparedParentQuery = checkNotNull(preparedParentQuery);
    this.entityComparator = new EntityComparator(query.getSortPredicates());
    this.txn = txn;
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
    return Iterables.size(asIterable(fetchOptions));
  }

  @Override
  public int countEntities() {
    return countEntities(withDefaults().limit(COUNT_ENTITIES_LEGACY_LIMIT));
  }

  static DatastoreV3Pb.Query.Order convertSortPredicateToPb(Query.SortPredicate predicate) {
    checkNotNull(predicate);
    DatastoreV3Pb.Query.Order order = new DatastoreV3Pb.Query.Order();
    order.setProperty(predicate.getPropertyName());
    order.setDirection(getSortOp(predicate.getDirection()));
    return order;
  }

  private static DatastoreV3Pb.Query.Order.Direction getSortOp(Query.SortDirection direction) {
    switch (direction) {
      case ASCENDING:
        return DatastoreV3Pb.Query.Order.Direction.ASCENDING;
      case DESCENDING:
        return DatastoreV3Pb.Query.Order.Direction.DESCENDING;
      default:
        throw new IllegalArgumentException("direction: " + direction);
    }
  }

  private OverlayQueryResultIteratorImpl runQuery(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    FetchOptions overlayFetchOptions = cloneFetchOptionsPrefetchAndChunkSize(fetchOptions);
    FetchOptions parentFetchOptions = cloneFetchOptionsPrefetchAndChunkSize(fetchOptions);
    QueryResultIterator<Entity> overlayIterator =
        preparedOverlayQuery.asQueryResultIterator(overlayFetchOptions);
    QueryResultIterator<Entity> parentIterator =
        preparedParentQuery.asQueryResultIterator(parentFetchOptions);
    return new OverlayQueryResultIteratorImpl(overlayIterator, parentIterator, fetchOptions);
  }

  private FetchOptions cloneFetchOptionsPrefetchAndChunkSize(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    FetchOptions clonedOptions = withDefaults();
    Integer prefetchSize = fetchOptions.getPrefetchSize();
    if (prefetchSize != null) {
      clonedOptions.prefetchSize(prefetchSize);
    }
    Integer chunkSize = fetchOptions.getChunkSize();
    if (chunkSize != null) {
      clonedOptions.chunkSize(chunkSize);
    }
    return clonedOptions;
  }

  private FetchOptions cloneFetchOptions(FetchOptions fetchOptions) {
    checkNotNull(fetchOptions);
    FetchOptions clonedOptions = cloneFetchOptionsPrefetchAndChunkSize(fetchOptions);
    Cursor startCursor = fetchOptions.getStartCursor();
    if (startCursor != null) {
      clonedOptions.startCursor(startCursor);
    }
    Cursor endCursor = fetchOptions.getEndCursor();
    if (endCursor != null) {
      clonedOptions.endCursor(endCursor);
    }
    Integer limit = fetchOptions.getLimit();
    if (limit != null) {
      clonedOptions.limit(limit);
    }
    Integer offset = fetchOptions.getOffset();
    if (offset != null) {
      clonedOptions.offset(offset);
    }
    return clonedOptions;
  }

  /**
   * A comparator for {@link Entity} instances that delegates to an {@link EntityProtoComparator}.
   */
  private static final class EntityComparator implements Comparator<Entity> {
    private final EntityProtoComparator delegate;

    EntityComparator(List<Query.SortPredicate> sortPreds) {
      checkNotNull(sortPreds);
      delegate = new EntityProtoComparator(
          sortPredicatesToOrders(sortPreds));
    }

    private static List<DatastoreV3Pb.Query.Order> sortPredicatesToOrders(
        List<Query.SortPredicate> sortPreds) {
      checkNotNull(sortPreds);
      ImmutableList.Builder<DatastoreV3Pb.Query.Order> orders =
          ImmutableList.<DatastoreV3Pb.Query.Order>builder();
      for (Query.SortPredicate sp : sortPreds) {
        orders.add(convertSortPredicateToPb(sp));
      }
      return orders.build();
    }

    @Override
    public int compare( Entity e1, Entity e2) {
      return delegate.compare(EntityTranslator.convertToPb(e1), EntityTranslator.convertToPb(e2));
    }
  }

  /**
   * An implementation of {@link QueryResultIterator} for use with an overlay-based
   * {@link DatastoreService}.
   *
   * <p>We run the input query on the parent, and then run it again on the overlay. We then merge
   * the two queries. As with {@code PreparedMultiQuery}, we take advantage of the fact that the
   * results from each query are already sorted (if a sort order was specified), and we interleave
   * the results from the two queries. (If no sort order was specified, then the order of results is
   * implementation-defined, anyway.)
   *
   * <p>We maintain a combined queue of entities from both sources, from which we return entities to
   * the user. We also maintain separate queues for the overlay and parent queries. When the user
   * asks for more entities, we first check to see if the combined queue can satisfy their request.
   * If so, then we're done. If not, then we need to pull more elements from the component queues.
   * The merge algorithm is iterative, and works something like this:
   *
   * <ul>
   *   <li>If either of the component queues is empty, refill it (in {@code chunkSize} chunks).
   *   Make sure that any "invalid" entities are removed. For the overlay query, that means that
   *   tombstones should be filtered out. For the parent entity, that means that if the overlay also
   *   has an entity with the same key as a given parent entity, then the parent entity should be
   *   filtered out. This work is done in batches to the greatest extent possible.
   *
   *   <li>Once both component queues have some entities, merge them, by continually taking the
   *   entity that sorts first (from whichever queue) and appending it to the end of the combined
   *   queue.
   *
   *   <li>In the event that one of the queries is completely depleted, just keep adding entities
   *   from the remaining query.
   * </ul>
   */
  private final class OverlayQueryResultIteratorImpl implements QueryResultBatchIterator<Entity> {
    private final QueryResultIterator<Entity> overlayIterator;
    private final QueryResultIterator<Entity> parentIterator;
    private final FetchOptions fetchOptions;
    private Queue<Entity> combinedEntityQueue;
    private Queue<Entity> overlayEntityQueue;
    private Queue<Entity> parentEntityQueue;
    private Integer remainingLimit;
    private int remainingOffset;

    /**
     * Constructs an overlay-based {@link QueryResultIterator<Entity>}.
     *
     * @param overlayIterator the iterator for the query on the overlay's backing Datastore
     * @param parentIterator the iterator for the query on the parent Datastore
     * @param fetchOptions the fetch options to apply
     */
    private OverlayQueryResultIteratorImpl(QueryResultIterator<Entity> overlayIterator,
        QueryResultIterator<Entity> parentIterator, FetchOptions fetchOptions) {
      this.overlayIterator = checkNotNull(overlayIterator);
      this.parentIterator = checkNotNull(parentIterator);
      this.fetchOptions = checkNotNull(fetchOptions);
      combinedEntityQueue = Queues.newArrayDeque();
      overlayEntityQueue = Queues.newArrayDeque();
      parentEntityQueue = Queues.newArrayDeque();
      this.remainingLimit = fetchOptions.getLimit();
      Integer offset = fetchOptions.getOffset();
      if (offset != null) {
        this.remainingOffset = offset;
      }
    }

    @Override
    public boolean hasNext() {
      while (remainingOffset > 0) {
        if (ensureLoaded(1) < 1) {
          remainingOffset = 0;
          return false;
        }
        takeNext();
        remainingOffset--;
      }
      return ensureLoaded(1) >= 1;
    }

    @Override
    public Entity next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      Entity next = takeNext();
      if (remainingLimit != null) {
        remainingLimit--;
        if (remainingLimit == 0) {
          combinedEntityQueue.clear();
          overlayEntityQueue.clear();
          parentEntityQueue.clear();
        }
      }
      return next;
    }

    @Override
    public List<Entity> nextList(int maximumElements) {
      ImmutableList.Builder<Entity> builder = ImmutableList.<Entity>builder();
      for (int i = 0; i < maximumElements; i++) {
        if (!hasNext()) {
          break;
        }
        builder.add(next());
      }
      return builder.build();
    }

    @Override
    public List<Index> getIndexList() {
      return parentIterator.getIndexList();
    }

    @Override
    public Cursor getCursor() {
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("QueryResultIterator does not support remove");
    }

    /**
     * Takes and returns the next entity from the iterator, assuming that there is such an entity.
     */
    private Entity takeNext() {
      return combinedEntityQueue.poll();
    }

    /**
     * Returns true if the overlay query has not yet been exhausted.
     */
    private boolean overlayQueryHasMore() {
      return (!overlayEntityQueue.isEmpty() || overlayIterator.hasNext());
    }

    /**
     * Returns true if the parent query has not yet been exhausted.
     */
    private boolean parentQueryHasMore() {
      return (!parentEntityQueue.isEmpty() || parentIterator.hasNext());
    }

    /**
     * Returns the chunk size, with a default of 1 if no chunk size was specified.
     */
    private int getChunkSize() {
      Integer chunkSize = fetchOptions.getChunkSize();
      return chunkSize != null ? chunkSize : 1;
    }

    /**
     * Refills the overlay entity queue from the overlay query iterator.
     */
    private void refillOverlayEntityQueue() {
      checkState(overlayEntityQueue.isEmpty());
      for (int i = 0; i < getChunkSize(); i++) {
        if (!overlayIterator.hasNext()) {
          break;
        }
        Entity e = overlayIterator.next();
        if (!OverlayUtils.isTombstone(e)) {
          overlayEntityQueue.add(e);
        }
      }
    }

    /**
     * Refills the parent entity queue from the parent query iterator.
     */
    private void refillParentEntityQueue() {
      checkState(parentEntityQueue.isEmpty());
      for (int i = 0; i < getChunkSize(); i++) {
        if (!parentIterator.hasNext()) {
          break;
        }
        parentEntityQueue.add(parentIterator.next());
      }

      Map<Key, Entity> overlayResults = overlay.getFromOverlayOnly(
          txn, OverlayUtils.getKeysAndTombstoneKeysForEntities(parentEntityQueue));
      Iterator<Entity> iterator = parentEntityQueue.iterator();
      while (iterator.hasNext()) {
        Entity entity = iterator.next();
        if (overlayResults.containsKey(entity.getKey())
            || overlayResults.containsKey(OverlayUtils.getTombstoneKey(entity.getKey()))) {
          iterator.remove();
        }
      }
    }

    /**
     * Merges elements from the overlay and parent queues into a single entity queue, respecting any
     * sort orders.
     *
     * @param numEntities the maximum number of entities that should be in the entity queue when
     *        this method completes.
     */
    private void mergeEntityQueues(int numEntities) {
      if (overlayEntityQueue.isEmpty()) {
        checkState(!overlayIterator.hasNext());
        if (parentEntityQueue.isEmpty()) {
          checkState(!parentIterator.hasNext());
        } else {
          while (!parentEntityQueue.isEmpty()) {
            combinedEntityQueue.add(parentEntityQueue.poll());
          }
        }
        return;
      } else if (parentEntityQueue.isEmpty()) {
        checkState(!parentIterator.hasNext());
        while (!overlayEntityQueue.isEmpty()) {
          combinedEntityQueue.add(overlayEntityQueue.poll());
        }
        return;
      }

      for (int i = combinedEntityQueue.size(); i < numEntities; i++) {
        if (overlayEntityQueue.isEmpty() || parentEntityQueue.isEmpty()) {
          return;
        }

        int result = entityComparator.compare(overlayEntityQueue.peek(), parentEntityQueue.peek());
        if (result < 0) {
          combinedEntityQueue.add(overlayEntityQueue.poll());
        } else {
          combinedEntityQueue.add(parentEntityQueue.poll());
        }
      }
    }

    /**
     * Requests additional {@code Entity} instances so that there are at least {@code numEntities}
     * entities available (or both iterators are exhausted). If there is a limit, stops requesting
     * entities once the limit has been reached. Does not take the offset into account.
     *
     * @param numEntities the number of entities that should be in the entity queue when this
     *        method completes
     * @return the number of entities that are now available
     */
    private int ensureLoaded(int numEntities) {
      if (remainingLimit != null && remainingLimit == 0) {
        return 0;
      }

      while (combinedEntityQueue.size() < numEntities) {
        if (!overlayQueryHasMore() && !parentQueryHasMore()) {
          return combinedEntityQueue.size();
        }
        while (overlayEntityQueue.isEmpty() && overlayIterator.hasNext()) {
          refillOverlayEntityQueue();
        }
        while (parentEntityQueue.isEmpty() && parentIterator.hasNext()) {
          refillParentEntityQueue();
        }
        mergeEntityQueues(numEntities);
      }

      return combinedEntityQueue.size();
    }
  }
}

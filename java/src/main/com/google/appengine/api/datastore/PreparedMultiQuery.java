// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;
import com.google.common.base.Function;
import com.google.common.base.Pair;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * A {@link PreparedQuery} implementation for use with {@link MultiQueryBuilder}.
 *
 * We run each successively generated list of filters returned by each
 * {@link MultiQueryBuilder} as they are needed and concatenate the result.
 *
 * If a list of filters contains more than one entry or there are multiple
 * {@link MultiQueryBuilder}s we build a {@link Comparator} based on the sort
 * predicates of the base query. We then use this {@link Comparator} to produce
 * an appropriately ordered sequence of results that contains the results from
 * each sub-query. As each sub-query produces results that are already sorted
 * we simply use a {@link PriorityQueue} to merge the results from the sub-query
 * as new results are requested.
 *
 */
class PreparedMultiQuery extends BasePreparedQuery {
  static final int MAX_BUFFERED_QUERIES = 10;

  private final Query baseQuery;
  private final List<MultiQueryBuilder> queryBuilders;
  private final EntityComparator entityComparator;
  private final Transaction txn;
  private final QueryRunner queryRunner;
  private final Set<String> projected;

  private final int[] maxBufferedIteratorsPerBuilder;

  /**
   * @param apiConfig the api config to use
   * @param datastoreServiceConfig the datastore service config to use
   * @param baseQuery the base query on which to apply generate filters filters
   * @param queryBuilders the source of filters to use
   * @param txn the txn in which all queries should execute, can be {@code null}
   *
   * @throws IllegalArgumentException if this multi-query required in memory
   * sorting and the base query is both a keys-only query and sorted by anything
   * other than its key.
   */
  PreparedMultiQuery(Query baseQuery, List<MultiQueryBuilder> queryBuilders, Transaction txn,
      QueryRunner queryRunner) {
    checkArgument(!queryBuilders.isEmpty());
    checkArgument(baseQuery.getFilter() == null);
    checkArgument(baseQuery.getFilterPredicates().isEmpty());
    this.txn = txn;
    this.baseQuery = baseQuery;
    this.queryBuilders = queryBuilders;
    this.queryRunner = queryRunner;

    if (baseQuery.getProjections().isEmpty()) {
      projected = Collections.emptySet();
    } else {
      projected = Sets.newHashSet();
      for (Projection proj : baseQuery.getProjections()) {
        projected.add(proj.getPropertyName());
      }
      if (!baseQuery.getSortPredicates().isEmpty()) {
        Set<String> localProjected = Sets.newHashSet(projected);
        for (SortPredicate sort : baseQuery.getSortPredicates()) {
          if (localProjected.add(sort.getPropertyName())) {
            baseQuery.addProjection(new PropertyProjection(sort.getPropertyName(), null));
          }
        }
      }
    }

    if (queryBuilders.size() > 1 || queryBuilders.get(0).getParallelQuerySize() > 1) {
      if (baseQuery.isKeysOnly()) {
        for (SortPredicate sp : baseQuery.getSortPredicates()) {
          if (!sp.getPropertyName().equals(Entity.KEY_RESERVED_PROPERTY)) {
            throw new IllegalArgumentException(
                "The provided keys-only multi-query needs to perform some "
                + "sorting in memory.  As a result, this query can only be "
                + "sorted by the key property as this is the only property "
                + "that is available in memory.");
          }
        }
      }
      List<SortPredicate> sortPredicates = baseQuery.getSortPredicates();
      List<Order> orders = new ArrayList<>(sortPredicates.size());
      for (SortPredicate sp : sortPredicates) {
        orders.add(QueryTranslator.convertSortPredicateToPb(sp));
      }
      this.entityComparator = new EntityComparator(orders);
    } else {
      this.entityComparator = null;
    }

    maxBufferedIteratorsPerBuilder = new int[queryBuilders.size()];
    int allocatableQueries = MAX_BUFFERED_QUERIES;

    for (int i = 0; i < queryBuilders.size(); i++) {
      ++maxBufferedIteratorsPerBuilder[i];
      allocatableQueries -= queryBuilders.get(i).getParallelQuerySize();
    }

    boolean madeEmptyPass = false;
    while (allocatableQueries > 0 && !madeEmptyPass) {
      madeEmptyPass = true;
      for (int i = 0; i < queryBuilders.size(); i++) {
        if (queryBuilders.get(i).getParallelQuerySize() <= allocatableQueries) {
          ++maxBufferedIteratorsPerBuilder[i];
          allocatableQueries -= queryBuilders.get(i).getParallelQuerySize();
          madeEmptyPass = false;
        }
      }
    }
  }

  protected PreparedQuery prepareQuery(List<FilterPredicate> filters, boolean isCountQuery) {
    Query query = new Query(baseQuery);
    if (isCountQuery && query.getProjections().isEmpty()) {
      query.setKeysOnly();
    }

    query.getFilterPredicates().addAll(filters);
    return new PreparedQueryImpl(query, txn, queryRunner);
  }

  protected Object getDedupeValue(Entity entity) {
    if (projected.isEmpty()) {
      return entity.getKey();
    } else {
      return Pair.of(entity.getKey(), entity.getProperties());
    }
  }

  /**
   * A helper function to prepare batches of queries.
   * @param filtersList list of the filters for each query to prepare
   * @return a list of prepared queries
   */
  protected List<PreparedQuery> prepareQueries(List<List<FilterPredicate>> filtersList) {
    List<PreparedQuery> preparedQueries = new ArrayList<PreparedQuery>(filtersList.size());
    for (List<FilterPredicate> filters : filtersList) {
      preparedQueries.add(prepareQuery(filters, false));
    }
    return preparedQueries;
  }

  /**
   * An iterator that will correctly process the values returned by a multiquery iterator.
   *
   * This iterator in some cases may not respect the provided FetchOptions.limit().
   */
  private class FilteredMultiQueryIterator extends AbstractIterator<Entity> {
    private final Iterator<List<List<FilterPredicate>>> multiQueryIterator;
    private final FetchOptions fetchOptions;
    private final Set<Object> seenUniqueValues;

    private Iterator<Entity> currentIterator = Collections.emptyIterator();
    private Queue<Iterator<Entity>> queryIterBuffer;

    public FilteredMultiQueryIterator(MultiQueryBuilder queryBuilder, FetchOptions fetchOptions,
        Set<Object> seenUniqueValues, int numIteratorsToBuffer) {
      this.multiQueryIterator = queryBuilder.iterator();
      this.queryIterBuffer = new ArrayDeque<Iterator<Entity>>(numIteratorsToBuffer);
      this.fetchOptions = fetchOptions;
      this.seenUniqueValues = seenUniqueValues;

      while (queryIterBuffer.size() < numIteratorsToBuffer && multiQueryIterator.hasNext()) {
        queryIterBuffer.add(makeQueryIterator());
      }
    }

    /**
     * Get the iterator for the next source query.  queryIterBuffer is already filled by
     * the {@link FilteredMultiQueryIterator} constructor.
     * We try to refill any slots in queryIterBuffer that we free up this way to pre-warm
     * the next query/queries.
     * @return iterator for the next source that has results or null if there isn't another source.
     */
    protected Iterator<Entity> getNextIterator() {
      while (!queryIterBuffer.isEmpty()) {
        Iterator<Entity> result = queryIterBuffer.remove();
        if (multiQueryIterator.hasNext()) {
          queryIterBuffer.add(makeQueryIterator());
        }
        if (result.hasNext()) {
          return result;
        }
      }
      return null;
    }

    /**
     * Create a iterator on a source query, either directly from a single query if possible or
     * by wrapping multiple queries that need to be mergesorted inside of a {@link HeapIterator}.
     * @return an iterator on the next {@link MultiQueryBuilder} from queryBuilders.
     */
    private Iterator<Entity> makeQueryIterator() {
      List<PreparedQuery> queries = prepareQueries(multiQueryIterator.next());
      if (queries.size() == 1) {
       return queries.get(0).asIterator(fetchOptions);
      } else {
        return makeHeapIterator(Iterables.transform(queries,
            new Function<PreparedQuery, Iterator<Entity>>() {
          @Override
          public Iterator<Entity> apply(PreparedQuery input) {
            return input.asIterator(fetchOptions);
          }
        }));
      }
    }

    @Override
    protected Entity computeNext() {
      Entity result = null;
      do {
        if (!currentIterator.hasNext()) {
          currentIterator = getNextIterator();
          if (currentIterator == null) {
            return endOfData();
          }
        }
        result = currentIterator.next();
      } while (!seenUniqueValues.add(getDedupeValue(result)));

      if (!projected.isEmpty()) {
        for (String prop : result.getProperties().keySet()) {
          if (!projected.contains(prop)) {
            result.removeProperty(prop);
          }
        }
      }
      return result;
    }
  }

  static final class HeapIterator extends AbstractIterator<Entity> {
    private final PriorityQueue<EntitySource> heap;

    HeapIterator(PriorityQueue<EntitySource> heap) {
      this.heap = heap;
    }

    @Override
    protected Entity computeNext() {
      Entity result;
      result = nextResult(heap);
      if (result == null) {
        endOfData();
      }
      return result;
    }
  }

  Iterator<Entity> makeHeapIterator(Iterable<Iterator<Entity>> iterators) {
    final PriorityQueue<EntitySource> heap = new PriorityQueue<EntitySource>();
    for (Iterator<Entity> iter : iterators) {
      if (iter.hasNext()) {
        heap.add(new EntitySource(entityComparator, iter));
      }
    }
    return new HeapIterator(heap);
  }

  /**
   * Fetch the next result from the {@link PriorityQueue} and reset the
   * datasource from which the next result was taken.
   */
  static Entity nextResult(PriorityQueue<EntitySource> availableEntitySources) {
    EntitySource current = availableEntitySources.poll();
    if (current == null) {
      return null;
    }
    Entity result = current.currentEntity;
    current.advance();
    if (current.currentEntity != null) {
      availableEntitySources.add(current);
    } else {
    }
    return result;
  }

  /**
   * Data structure that we use in conjunction with the {@link PriorityQueue}.
   * It always compares using its {@code currentEntity} field by delegating to
   * its {@code entityComparator}.
   */
  static final class EntitySource implements Comparable<EntitySource> {
    private final EntityComparator entityComparator;
    private final Iterator<Entity> source;
    private Entity currentEntity;

    EntitySource(EntityComparator entityComparator, Iterator<Entity> source) {
      this.entityComparator = entityComparator;
      this.source = source;
      if (!source.hasNext()) {
        throw new IllegalArgumentException("Source iterator has no data.");
      }
      this.currentEntity = source.next();
    }

    private void advance() {
      currentEntity = source.hasNext() ? source.next() : null;
    }

    @Override
    public int compareTo(EntitySource entitySource) {
      return entityComparator.compare(currentEntity, entitySource.currentEntity);
    }
  }

  @Override
  public Entity asSingleEntity() throws TooManyResultsException {
    List<Entity> result = this.asList(FetchOptions.Builder.withLimit(2));
    if (result.size() == 1) {
      return result.get(0);
    } else if (result.size() > 1) {
      throw new TooManyResultsException();
    } else {
      return null;
    }
  }

  @Override
  public int countEntities(FetchOptions fetchOptions) {
    FetchOptions overrideOptions = new FetchOptions(fetchOptions);
    overrideOptions.chunkSize(Integer.MAX_VALUE);
    if (fetchOptions.getOffset() != null) {
      overrideOptions.clearOffset();
      if (fetchOptions.getLimit() != null) {
        int adjustedLimit = fetchOptions.getOffset() + fetchOptions.getLimit();
        if (adjustedLimit < 0) {
          overrideOptions.clearLimit();
        } else {
          overrideOptions.limit(adjustedLimit);
        }
      }
    }

    Set<Object> seen = Sets.newHashSet();

outer:
    for (MultiQueryBuilder queryBuilder : queryBuilders) {
      for (List<List<FilterPredicate>> filtersList : queryBuilder) {
        for (List<FilterPredicate> filters : filtersList) {
          PreparedQuery preparedQuery = prepareQuery(filters, true);
          Query query = new Query(baseQuery);
          if (query.getProjections().isEmpty()) {
            query.setKeysOnly();
          }
          for (Entity entity : preparedQuery.asIterable(overrideOptions)) {
            if (seen.add(getDedupeValue(entity)) && overrideOptions.getLimit() != null
                && seen.size() >= overrideOptions.getLimit()) {
              break outer;
            }
          }
        }
      }
    }
    return fetchOptions.getOffset() == null
        ? seen.size() : Math.max(0, seen.size() - fetchOptions.getOffset());
  }

  @Override
  public Iterator<Entity> asIterator(FetchOptions fetchOptions) {

    if ((fetchOptions.getOffset() != null && fetchOptions.getOffset() > 0)
        || fetchOptions.getLimit() != null) {
      FetchOptions override = new FetchOptions(fetchOptions);
      if (fetchOptions.getOffset() != null) {
        override.clearOffset();
        if (fetchOptions.getLimit() != null) {
          int adjustedLimit = fetchOptions.getOffset() + fetchOptions.getLimit();
          if (adjustedLimit < 0) {
            override.clearLimit();
          } else {
            override.limit(adjustedLimit);
          }
        }
      }
      return new SlicingIterator<Entity>(
          newFilteredMultiQueryIterator(override),
          fetchOptions.getOffset(),
          fetchOptions.getLimit());
    } else {
      return newFilteredMultiQueryIterator(fetchOptions);
    }
  }

  private Iterator<Entity> newFilteredMultiQueryIterator(FetchOptions fetchOptions) {
    Set<Object> dedupeSet = Sets.newHashSet();
    if (queryBuilders.size() == 1) {
      return new FilteredMultiQueryIterator(queryBuilders.get(0), fetchOptions, dedupeSet,
          maxBufferedIteratorsPerBuilder[0]);
    }
    List<Iterator<Entity>> iterators = Lists.newArrayListWithCapacity(queryBuilders.size());
    for (int i = 0; i < queryBuilders.size(); i++) {
      iterators.add(new FilteredMultiQueryIterator(queryBuilders.get(i), fetchOptions, dedupeSet,
          maxBufferedIteratorsPerBuilder[i]));
    }
    return makeHeapIterator(iterators);
  }

  @Override
  public List<Entity> asList(FetchOptions fetchOptions) {
    FetchOptions override = new FetchOptions(fetchOptions);
    if (override.getChunkSize() == null) {
      override.chunkSize(Integer.MAX_VALUE);
    }

    List<Entity> results = new ArrayList<Entity>();
    Iterables.addAll(results, asIterable(override));
    return results;
  }

  private static class NullQueryResult implements QueryResult {

    public static final NullQueryResult INSTANCE = new NullQueryResult();

    @Override
    public List<Index> getIndexList() {
      return null;
    }

    @Override
    public Cursor getCursor() {
      return null;
    }

  }

  @Override
  public QueryResultIterator<Entity> asQueryResultIterator(FetchOptions fetchOptions) {
    return new QueryResultIteratorDelegator<Entity>(new NullQueryResult(),
                                                    asIterator(fetchOptions));
  }

  @Override
  public QueryResultList<Entity> asQueryResultList(FetchOptions fetchOptions) {
    return new QueryResultListDelegator<Entity>(NullQueryResult.INSTANCE,
                                                asList(fetchOptions));
  }
}

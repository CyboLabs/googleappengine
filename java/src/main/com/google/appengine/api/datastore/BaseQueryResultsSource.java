// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Concrete implementation of QueryResultsSource which knows how to
 * make callbacks back into the datastore to retrieve more entities
 * for the specified cursor.
 *
 */
abstract class BaseQueryResultsSource<InitialResultT, NextRequestT, NextResultT>
    implements QueryResultsSource {

  /**
   * A common interface for working with a query result.
   */
  interface WrappedQueryResult {
    /**
     * Get the end cursor associated with the wrapped query result.
     */
    Cursor getEndCursor();

    /**
     * Get the entities included in the wrapped query result.
     * @param projections Projections from the initial {@link Query}.
     */
    List<Entity> getEntities(Collection<Projection> projections);

    /**
     * A list of Cursor objections which correspond to the entities returned
     * by {@link #getEntities(Collection)}.
     */
    List< Cursor> getResultCursors();

    /**
     * Return the cursor just after the last result skipped due to an offset.
     */ Cursor getSkippedResultsCursor();

    /**
     * Indices whether a {@link #makeNextCall(Object, WrappedQueryResult, Integer, Integer)} call
     * with the wrapped object will return more results.
     */
    boolean hasMoreResults();

    /**
     * The number of results skipped over due to an offset.
     */
    int numSkippedResults();

    /**
     * Parses information about the indexes used in the query.
     *
     * @param monitoredIndexBuffer Indexes with the 'only use if required' flag set will be
     * added to this buffer.
     * @returns CompositeIndexes which were used in the query.
     */
    List<Index> getIndexInfo(Collection<Index> monitoredIndexBuffer);
  }

  static Logger logger = Logger.getLogger(BaseQueryResultsSource.class.getName());
  private static final int AT_LEAST_ONE = -1;
  private static final String DISABLE_CHUNK_SIZE_WARNING_SYS_PROP =
      "appengine.datastore.disableChunkSizeWarning";
  private static final int CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD = 1000;
  private static final long MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS = 1000 * 60 * 5;
  static MonitoredIndexUsageTracker monitoredIndexUsageTracker = new MonitoredIndexUsageTracker();
  static final AtomicLong lastChunkSizeWarning = new AtomicLong(0);

  private final DatastoreCallbacks callbacks;
  private final int chunkSize;
  private final int offset;
  private final Transaction txn;
  private final Query query;
  private final CurrentTransactionProvider currentTransactionProvider;

  private Future<NextResultT> queryResultFuture = null;
  private int skippedResults;
  private int totalResults = 0;
  private List<Index> indexList = null;
  private boolean addedSkippedCursor;
  private final Future<InitialResultT> initialQueryResultFuture;

  /**
   * Prototype for next/continue requests.
   * This field remains null until initialQueryResultFuture is processed.
   */
  private NextRequestT nextQueryPrototype = null;

  public BaseQueryResultsSource(DatastoreCallbacks callbacks, FetchOptions fetchOptions,
      final Transaction txn, Query query, Future<InitialResultT> initialQueryResultFuture) {
    this.callbacks = callbacks;
    this.chunkSize = fetchOptions.getChunkSize() != null
        ? fetchOptions.getChunkSize() : AT_LEAST_ONE;
    this.offset = fetchOptions.getOffset() != null ? fetchOptions.getOffset() : 0;
    this.txn = txn;
    this.query = query;
    this.currentTransactionProvider = new CurrentTransactionProvider() {
      @Override
      public Transaction getCurrentTransaction(Transaction defaultValue) {
        return txn;
      }
    };
    this.initialQueryResultFuture = initialQueryResultFuture;
    this.skippedResults = 0;
  }

  /**
   * Wrap an initial query result and provide a standard interface for data extraction.
   */
  abstract WrappedQueryResult wrapInitialResult(InitialResultT res);

  /**
   * Wrap a continue query request and provide a standard interface for data extraction.
   */
  abstract WrappedQueryResult wrapResult(NextResultT res);

  /**
   * Construct base object for continuing queries if more results are needed. This object
   * will passed into {@link #makeNextCall(Object, WrappedQueryResult, Integer, Integer)}.
   */
  abstract NextRequestT buildNextCallPrototype(InitialResultT res);

  /**
   * Issue a continue query request to the {@link AsyncDatastoreService}.
   * @returns The future containing the result.
   */
  abstract Future<NextResultT> makeNextCall(NextRequestT prototype,
      WrappedQueryResult latestResult, Integer fetchCountOrNull, Integer offsetOrNull);

  @Override
  public boolean hasMoreEntities() {
    return (nextQueryPrototype == null || queryResultFuture != null);
  }

  @Override
  public int getNumSkipped() {
    return skippedResults;
  }

  @Override
  public List<Index> getIndexList() {
    if (indexList == null) {
      InitialResultT res = FutureHelper.quietGet(initialQueryResultFuture);
      Set<Index> monitoredIndexBuffer = Sets.newHashSet();
      indexList = wrapInitialResult(res).getIndexInfo(monitoredIndexBuffer);
      if (!monitoredIndexBuffer.isEmpty()) {
        monitoredIndexUsageTracker.addNewUsage(monitoredIndexBuffer, query);
      }
    }
    return indexList;
  }

  @Override
  public Cursor loadMoreEntities(List<Entity> buffer, List<Cursor> cursorBuffer) {
    return loadMoreEntities(AT_LEAST_ONE, buffer, cursorBuffer);
  }

  @Override
  public Cursor loadMoreEntities(int numberToLoad, List<Entity> buffer, List<Cursor> cursorBuffer) {
    TransactionImpl.ensureTxnActive(txn);
    if (nextQueryPrototype == null || queryResultFuture != null) {
      if (numberToLoad == 0 &&
          offset <= skippedResults) {
        if (!addedSkippedCursor) {
          cursorBuffer.add(null);
          addedSkippedCursor = true;
        }
        return null;
      }
      WrappedQueryResult res;
      if (nextQueryPrototype == null) {
        getIndexList();
        InitialResultT initialRes = FutureHelper.quietGet(initialQueryResultFuture);
        nextQueryPrototype = buildNextCallPrototype(initialRes);
        res = wrapInitialResult(initialRes);
      } else {
        res = wrapResult(FutureHelper.quietGet(queryResultFuture));
        queryResultFuture = null;
      }

      int fetchedSoFar = processQueryResult(res, buffer, cursorBuffer);

      Integer fetchCountOrNull = null;
      Integer offsetOrNull = null;
      if (res.hasMoreResults()) {
        boolean setCount = true;
        if (numberToLoad <= 0) {
          setCount = false;
          if (chunkSize != AT_LEAST_ONE) {
            fetchCountOrNull = chunkSize;
          }
          if (numberToLoad == AT_LEAST_ONE) {
            numberToLoad = 1;
          }
        }

        while (res.hasMoreResults()
            && (skippedResults < offset
                || fetchedSoFar < numberToLoad)) {
          if (skippedResults < offset) {
            offsetOrNull = offset - skippedResults;
          } else {
            offsetOrNull = null;
          }
          if (setCount) {
            fetchCountOrNull = Math.max(chunkSize, numberToLoad - fetchedSoFar);
          }
          res = wrapResult(FutureHelper.quietGet(
              makeNextCall(nextQueryPrototype, res, fetchCountOrNull, offsetOrNull)));
          fetchedSoFar += processQueryResult(res, buffer, cursorBuffer);
        }
      }

      if (res.hasMoreResults()) {
        fetchCountOrNull = chunkSize != AT_LEAST_ONE ? chunkSize : null;
        offsetOrNull = null;
        queryResultFuture = makeNextCall(nextQueryPrototype, res, fetchCountOrNull, offsetOrNull);
      }
      return res.getEndCursor();
    }
    return null;
  }

  /**
   * Helper function to process the query results.
   *
   * This function adds results to the given buffer and updates {@link
   * #skippedResults}.
   *
   * @param res The {@link com.google.apphosting.datastore.DatastoreV3Pb.QueryResult} to process
   * @param buffer the buffer to which to add results
   * @returns The number of new results added to buffer.
   */
  private int processQueryResult(WrappedQueryResult res, List<Entity> buffer,
      List<Cursor> cursorBuffer) {
    skippedResults += res.numSkippedResults();
    if (skippedResults >= offset && !addedSkippedCursor) {
      cursorBuffer.add(res.getSkippedResultsCursor());
      addedSkippedCursor = true;
    }

    List<Entity> entityList = res.getEntities(query.getProjections());
    buffer.addAll(entityList);
    cursorBuffer.addAll(res.getResultCursors());
    for (Entity entity : entityList) {
      callbacks.executePostLoadCallbacks(new PostLoadContext(currentTransactionProvider, entity));
    }
    totalResults += entityList.size();
    if (chunkSize == AT_LEAST_ONE && totalResults > CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD
        && System.getProperty(DISABLE_CHUNK_SIZE_WARNING_SYS_PROP) == null) {
      logChunkSizeWarning();
    }
    return entityList.size();
  }

  void logChunkSizeWarning() {
    long now = System.currentTimeMillis();
    if ((now - lastChunkSizeWarning.get()) < MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS) {
      return;
    }
    logger.warning(
        "This query does not have a chunk size set in FetchOptions and has returned over "
            + CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD + " results.  If result sets of this "
            + "size are common for this query, consider setting a chunk size to improve "
            + "performance.\n  To disable this warning set the following system property in "
            + "appengine-web.xml (the value of the property doesn't matter): '"
            + DISABLE_CHUNK_SIZE_WARNING_SYS_PROP + "'");
    lastChunkSizeWarning.set(now);
  }
}

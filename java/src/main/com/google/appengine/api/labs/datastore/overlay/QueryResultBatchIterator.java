package com.google.appengine.api.labs.datastore.overlay;

import com.google.appengine.api.datastore.QueryResultIterator;

import java.util.List;

/**
 * An extension of {@link QueryResultIterator<T>} that includes the {@code nextList} method, for
 * efficient retrieval of multiple elements at a time.
 *
 * @param <T> the type of result returned by the query
 */
interface QueryResultBatchIterator<T> extends QueryResultIterator<T> {
  /**
   * Returns a {@link List<T>} of up to {@code maximumElements} elements. If there are fewer than
   * this many elements left to be retrieved, the {@link List<T>} returned will simply contain less
   * than {@code maximumElements} elements. In particular, calling this when there are no elements
   * remaining is not an error, it simply returns an empty list.
   */
  List<T> nextList(int maximumElements);
}

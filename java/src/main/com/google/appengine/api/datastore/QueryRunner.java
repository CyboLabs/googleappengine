package com.google.appengine.api.datastore;

/**
 * An interface for constructing and serializing and sending out queries. This code
 * is dependent on the wire format and specification.
 */
interface QueryRunner {
  QueryResultsSource runQuery(
      FetchOptions fetchOptions, Query query, Transaction txn);
}
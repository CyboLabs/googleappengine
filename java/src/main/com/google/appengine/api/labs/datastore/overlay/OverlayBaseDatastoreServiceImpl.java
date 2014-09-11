package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;

import java.util.Collection;
import java.util.Map;

/**
 * An implementation of {@link BaseDatastoreService} using an overlay model. Conceptually, an
 * overlay Datastore is based on some other Datastore (the "parent"). The overlay allows developers
 * to effectively update or delete entities on the parent, but without actually modifying the data
 * that the parent stores.
 */
abstract class OverlayBaseDatastoreServiceImpl implements BaseDatastoreService {
  @Override
  public PreparedQuery prepare(Query query) {
    checkNotNull(query);
    return prepare(null, query);
  }

  @Override
  public PreparedQuery prepare(Transaction txn, Query query) {
    PreparedQuery preparedOverlayQuery = getUnderlyingBaseDatastoreService().prepare(txn, query);
    PreparedQuery preparedParentQuery = getParentBaseDatastoreService().prepare(null, query);
    return new OverlayPreparedQueryImpl(this, query, preparedOverlayQuery, preparedParentQuery,
        txn);
  }

  @Override
  public Transaction getCurrentTransaction() {
    return getUnderlyingBaseDatastoreService().getCurrentTransaction();
  }

  @Override
  public Transaction getCurrentTransaction( Transaction returnedIfNoTxn) {
    return getUnderlyingBaseDatastoreService().getCurrentTransaction(returnedIfNoTxn);
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return getUnderlyingBaseDatastoreService().getActiveTransactions();
  }

  /**
   * Gets the entities corresponding to the given keys, searching only the overlay itself. Does not
   * check whether the parent Datastore has any matching entities.
   */
  abstract Map<Key, Entity> getFromOverlayOnly( Transaction txn, Iterable<Key> keys);

  /**
   * Gets the parent Datastore.
   */
  protected abstract BaseDatastoreService getParentBaseDatastoreService();

  /**
   * Gets the underlying Datastore.
   */
  protected abstract BaseDatastoreService getUnderlyingBaseDatastoreService();
}

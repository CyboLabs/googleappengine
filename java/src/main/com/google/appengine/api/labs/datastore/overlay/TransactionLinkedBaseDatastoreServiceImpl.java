package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;

import java.util.Collection;

/**
 * A simple wrapper class that combines a {@link BaseDatastoreService} with a {@link Transaction}.
 * The purpose of this class is to avoid code duplication between the transaction and
 * non-transaction versions of several API methods.
 */
abstract class TransactionLinkedBaseDatastoreServiceImpl implements BaseDatastoreService {
  protected final Transaction txn;

  public TransactionLinkedBaseDatastoreServiceImpl( Transaction txn) {
    this.txn = txn;
  }

  @Override
  public PreparedQuery prepare(Query query) {
    checkNotNull(query);
    return getUnderlyingBaseDatastoreService().prepare(txn, query);
  }

  @Override
  public PreparedQuery prepare( Transaction txn, Query query) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
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
   * Returns the underlying {@link BaseDatastoreService}.
   */
  protected abstract BaseDatastoreService getUnderlyingBaseDatastoreService();
}

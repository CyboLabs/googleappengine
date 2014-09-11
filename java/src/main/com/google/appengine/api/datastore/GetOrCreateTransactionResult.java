package com.google.appengine.api.datastore;

/**
 * Helper class used to encapsulate the result of a call to
 * {@link BaseAsyncDatastoreServiceImpl#getOrCreateTransaction()}.
 */
final class GetOrCreateTransactionResult {

  private final boolean isNew;
  private final Transaction txn;

  GetOrCreateTransactionResult(boolean isNew,Transaction txn) {
    this.isNew = isNew;
    this.txn = txn;
  }

  /**
   * @return {@code true} if the Transaction was created and should therefore
   * be closed before the end of the operation, {@code false} otherwise.
   */
  public boolean isNew() {
    return isNew;
  }

  /**
   * @return The Transaction to use.  Can be {@code null}.
   */
  public Transaction getTransaction() {
    return txn;
  }
}

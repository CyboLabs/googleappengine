// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.appengine.api.datastore.EntityCachingStrategy.PreMutationCachingResult;
import com.google.appengine.api.utils.FutureWrapper;

import java.util.List;
import java.util.concurrent.Future;

/**
 * State and behavior that is common to all {@link Transaction} implementations.
 *
 * Our implementation is implicitly async. BeginTransaction RPCs always return
 * instantly, and this class maintains a reference to the {@link Future}
 * associated with the RPC.  We service as much of the {@link Transaction}
 * interface as we can without retrieving the result of the future.
 *
 * There is no synchronization in this code because transactions are associated
 * with a single thread and are documented as such.
 */
class TransactionImpl implements Transaction, CurrentTransactionProvider {

  /**
   * Interface to a coupled object which handles the actual transaction RPCs
   * and other service protocol dependent details.
   */
  interface InternalTransaction {
    /**
     * Issues an asynchronous RPC to commit this transaction.
     */
    Future<Void> doCommitAsync();

    /**
     * Issues an asynchronous RPC to rollback this transaction.
     */
    Future<Void> doRollbackAsync();

    String getId();

    @Override
    boolean equals(Object o);

    @Override
    int hashCode();
  }

  enum TransactionState {
    BEGUN,
    COMPLETION_IN_PROGRESS,
    COMMITTED,
    ROLLED_BACK,
    ERROR
  }

  private final String app;

  private final TransactionStack txnStack;

  private final DatastoreCallbacks callbacks;

  private final EntityCachingStrategy entityCachingStrategy;

  private final boolean isExplicit;

  private final InternalTransaction internalTransaction;

  TransactionState state = TransactionState.BEGUN;

  /**
   * A {@link PostOpFuture} implementation that runs both post put and post
   * delete callbacks.
   */
  private class PostCommitFuture extends PostOpFuture<Void> {
    private final List<Entity> putEntities;
    private final List<Key> deletedKeys;

    private PostCommitFuture(
        List<Entity> putEntities, List<Key> deletedKeys, Future<Void> delegate) {
      super(delegate, callbacks);
      this.putEntities = putEntities;
      this.deletedKeys = deletedKeys;
    }

    @Override
    void executeCallbacks(Void ignoreMe) {
      PutContext putContext = new PutContext(TransactionImpl.this, putEntities);
      callbacks.executePostPutCallbacks(putContext);
      DeleteContext deleteContext = new DeleteContext(TransactionImpl.this, deletedKeys);
      callbacks.executePostDeleteCallbacks(deleteContext);
    }
  }

  TransactionImpl(String app, TransactionStack txnStack,
      DatastoreCallbacks callbacks, EntityCachingStrategy entityCachingStrategy,
      boolean isExplicit, InternalTransaction txnProvider) {
    this.app = app;
    this.txnStack = txnStack;
    this.callbacks = callbacks;
    this.entityCachingStrategy = entityCachingStrategy;
    this.isExplicit = isExplicit;
    this.internalTransaction = txnProvider;
  }

  @Override
  public String getId() {
    return internalTransaction.getId();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof TransactionImpl) {
      return internalTransaction.equals(((TransactionImpl) o).internalTransaction);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return internalTransaction.hashCode();
  }

  @Override
  public void commit() {
    FutureHelper.quietGet(commitAsync());
  }

  @Override
  public Future<Void> commitAsync() {
    ensureTxnStarted();
    try {
      for (Future<?> f : txnStack.getFutures(this)) {
        FutureHelper.quietGet(f);
      }
      PreMutationCachingResult preMutationCachingResult =
          entityCachingStrategy.preCommit(txnStack.getPutEntities(this),
              txnStack.getDeletedKeys(this));
      Future<Void> commitResponse = internalTransaction.doCommitAsync();
      state = TransactionState.COMPLETION_IN_PROGRESS;
      Future<Void> result = new FutureWrapper<Void, Void>(commitResponse) {
        @Override
        protected Void wrap(Void ignore) throws Exception {
          state = TransactionState.COMMITTED;
          return null;
        }

        @Override
        protected Throwable convertException(Throwable cause) {
          state = TransactionState.ERROR;
          return cause;
        }
      };
      result = entityCachingStrategy.createPostMutationFuture(result, preMutationCachingResult);
      return new PostCommitFuture(txnStack.getPutEntities(this), txnStack.getDeletedKeys(this),
          result);
    } finally {
      if (isExplicit) {
        txnStack.remove(this);
      }
    }
  }

  @Override
  public void rollback() {
    FutureHelper.quietGet(rollbackAsync());
  }

  @Override
  public Future<Void> rollbackAsync() {
    ensureTxnStarted();
    try {
      for (Future<?> f : txnStack.getFutures(this)) {
        FutureHelper.quietGet(f);
      }
      Future<Void> future = internalTransaction.doRollbackAsync();
      state = TransactionState.COMPLETION_IN_PROGRESS;
      return new FutureWrapper<Void, Void>(future) {
        @Override
        protected Void wrap(Void ignore) throws Exception {
          state = TransactionState.ROLLED_BACK;
          return null;
        }

        @Override
        protected Throwable convertException(Throwable cause) {
          state = TransactionState.ERROR;
          return cause;
        }
      };
    } finally {
      if (isExplicit) {
        txnStack.remove(this);
      }
    }
  }

  @Override
  public String getApp() {
    return app;
  }

  @Override
  public boolean isActive() {
    return state == TransactionState.BEGUN || state == TransactionState.COMPLETION_IN_PROGRESS;
  }

  @Override
  public Transaction getCurrentTransaction(Transaction defaultValue) {
    return this;
  }

  /**
   * If {@code txn} is not null and not active, throw
   * {@link IllegalStateException}.
   */
  static void ensureTxnActive(Transaction txn) {
    if (txn != null && !txn.isActive()) {
      throw new IllegalStateException("Transaction with which this operation is "
          + "associated is not active.");
    }
  }

  private void ensureTxnStarted() {
    if (state != TransactionState.BEGUN) {
      throw new IllegalStateException("Transaction is in state " + state + ".  There is no legal "
          + "transition out of this state.");
    }
  }

  @Override
  public String toString() {
    return "Txn [" + app + "." + getId() + ", " + state + "]";
  }
}

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.FutureHelper.quietGet;

import com.google.appengine.api.utils.FutureWrapper;

import java.util.concurrent.Future;

/**
 * A class that knows how to run things inside transactions.
 *
 * @param <T> The type of result of the operation that we run in a transaction.
 *
 */
abstract class TransactionRunner<T> {

  private final Transaction txn;
  private final boolean finishTxn;

  protected TransactionRunner(GetOrCreateTransactionResult result) {
    txn = result.getTransaction();
    finishTxn = result.isNew();
    if (txn == null && finishTxn) {
      throw new IllegalArgumentException("Cannot have a null txn when finishTxn is true.  This "
          + "almost certainly represents a programming error on the part of the App Engine team.  "
          + "Please report this via standard support channels and accept our humblest apologies.");
    }
    TransactionImpl.ensureTxnActive(txn);
  }

  public Future<T> runReadInTransaction() {
    if (!finishTxn) {
      return runInternal(txn);
    }

    return new FutureWrapper<T, T>(runInternal(txn)) {
      @Override
      protected T wrap(T result) throws Exception {
        txn.commit();
        return result;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        txn.rollback();
        return cause;
      }
    };
  }

  public Future<T> runWriteInTransaction() {
    if (!finishTxn) {
      return runInternal(txn);
    }

    Future<Void> txnFuture;
    T result = null;
    Exception capturedException = null;
    try {
      result = quietGet(runInternal(txn));
    } catch (Exception e) {
      capturedException = e;
    } finally {
      if (capturedException == null) {
        txnFuture = txn.commitAsync();
      } else {
        txnFuture = txn.rollbackAsync();
      }
    }

    final T finalResult = result;
    final Exception finalCapturedException = capturedException;
    return new FutureWrapper<Void, T>(txnFuture) {
      @Override
      protected T wrap(Void v) throws Exception {
        if (finalCapturedException != null) {
          throw finalCapturedException;
        } else {
          return finalResult;
        }
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  protected abstract Future<T> runInternal(Transaction txn);
}

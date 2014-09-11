package com.google.appengine.api.labs.datastore.overlay;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A simple {@link Future} that works on a single thread. It does nothing until the first time that
 * {@code get} is called, at which point it is synchronously resolved.
 */
abstract class FakeFutureTask<T> extends RethrowingFutureWrapper<Void, T> {

  public FakeFutureTask() {
    super(new FakeEmptyFuture());
  }

  @Override
  protected final T wrap(Void v) throws Exception {
    return call();
  }

  protected abstract T call() throws Exception;

  /**
   * A {@link Future} that does absolutely nothing.
   */
  static final class FakeEmptyFuture implements Future<Void> {
    FakeEmptyFuture() { }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    @SuppressWarnings("unused")
    public Void get() throws ExecutionException {
      return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit) {
      return null;
    }
  }
}

package com.google.appengine.api.labs.datastore.overlay;

import com.google.appengine.api.utils.FutureWrapper;

import java.util.concurrent.Future;

/**
 * A simple {@link Future} that wraps a parent {@code Future}. Unlike {@link FutureWrapper}, this
 * class does not require the user to specify failure behavior. Any exception is passed through
 * unmodified. This class is thread-safe.
 *
 * @param <K> The type of this {@link Future}
 * @param <V> The type of the wrapped {@link Future}
 */
public abstract class RethrowingFutureWrapper<K, V> extends FutureWrapper<K, V> {
  public RethrowingFutureWrapper(Future<K> parent) {
    super(parent);
  }

  @Override
  protected Throwable convertException(Throwable cause) {
    return cause;
  }
}

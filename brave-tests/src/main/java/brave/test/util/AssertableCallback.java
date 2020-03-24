/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.test.util;

import brave.internal.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ObjectAssert;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A callback of a single result or error that supports assertions.
 *
 * <p>This is a bridge to async libraries such as CompletableFuture complete,
 * completeExceptionally.
 *
 * <p>Implementations will call either {@link #onSuccess} or {@link #onError}, but not both.
 */
public final class AssertableCallback<V> extends CountDownLatch implements
  BiConsumer<V, Throwable> {
  static final Object NULL_SENTINEL = new Object() {
    @Override public String toString() {
      return "null";
    }
  };

  final AtomicInteger onSuccessCount = new AtomicInteger(), onErrorCount = new AtomicInteger();
  Runnable listener = () -> {
  };
  Object result; // thread visibility guaranteed by the countdown latch
  Object error; // thread visibility guaranteed by the countdown latch

  public AssertableCallback() {
    super(1);
  }

  public AssertableCallback<V> setListener(Runnable listener) {
    if (listener == null) throw new NullPointerException("listener == null");
    this.listener = listener;
    return this;
  }

  /**
   * Invoked when computation produces its potentially null value successfully.
   *
   * <p>When this is called, {@link #onError} won't be.
   */
  public void onSuccess(@Nullable V value) {
    onSuccessCount.incrementAndGet();
    result = value != null ? value : NULL_SENTINEL;
    listener.run();
    countDown();
  }

  /**
   * Invoked when computation produces a possibly null value successfully.
   *
   * <p>When this is called, {@link #onSuccess} won't be.
   */
  public void onError(Throwable throwable) {
    onErrorCount.incrementAndGet();
    error = throwable != null ? throwable : NULL_SENTINEL;
    listener.run();
    countDown();
  }

  public ObjectAssert<V> assertThatSuccess() {
    awaitUninterruptably();

    if (onSuccessCount.get() > 0) {
      assertThat(onSuccessCount)
        .withFailMessage("onSuccess signaled multiple times")
        .hasValueLessThan(2);

      assertThat(onErrorCount)
        .withFailMessage("Both onSuccess and onError were signaled")
        .hasValue(0);

      return assertThat(result == NULL_SENTINEL ? null : (V) result);
    } else if (onErrorCount.get() > 0) {
      assertThat(error)
        .withFailMessage("onError signaled with null")
        .isNotSameAs(NULL_SENTINEL);

      throw new AssertionError("expected onSuccess, but received onError(" + error + ")",
        (Throwable) error);
    }
    return null;
  }

  public AbstractThrowableAssert<?, ? extends Throwable> assertThatError() {
    awaitUninterruptably();

    if (onErrorCount.get() > 0) {
      assertThat(onErrorCount)
        .withFailMessage("onError signaled multiple times")
        .hasValueLessThan(2);

      assertThat(onSuccessCount)
        .withFailMessage("Both onSuccess and onError were signaled")
        .hasValue(0);

      return assertThat(result == NULL_SENTINEL ? null : (Throwable) result);
    } else if (onSuccessCount.get() > 0) {
      throw new AssertionError("expected onError, but received onSuccess(" + result + ")");
    }
    throw new AssertionError(); // unexpected as we only have two callbacks to handle!
  }

  void awaitUninterruptably() {
    try {
      await(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Override public void accept(V v, Throwable throwable) {
    if (throwable != null) {
      onError(throwable);
    } else {
      onSuccess(v);
    }
  }
}

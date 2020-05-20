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
package brave.spring.web;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;

/**
 * Ensures callbacks run in the invocation trace context.
 *
 * <p>Note: {@link #completable()} is not instrumented to propagate the invocation trace context.
 */
final class TraceContextListenableFuture<T> implements ListenableFuture<T> {
  final ListenableFuture<T> delegate;
  final CurrentTraceContext currentTraceContext;
  final TraceContext invocationContext;

  TraceContextListenableFuture(ListenableFuture<T> delegate,
    CurrentTraceContext currentTraceContext, TraceContext invocationContext) {
    this.delegate = delegate;
    this.currentTraceContext = currentTraceContext;
    this.invocationContext = invocationContext;
  }

  @Override public void addCallback(ListenableFutureCallback<? super T> callback) {
    delegate.addCallback(callback != null
      ? new TraceContextListenableFutureCallback<>(callback, this)
      : null
    );
  }

  // Do not use @Override annotation to avoid compatibility issue version < 4.1
  public void addCallback(SuccessCallback<? super T> successCallback,
    FailureCallback failureCallback) {
    delegate.addCallback(
      successCallback != null
        ? new TraceContextSuccessCallback<>(successCallback, this)
        : null,
      failureCallback != null
        ? new TraceContextFailureCallback(failureCallback, this)
        : null
    );
  }

  // Do not use @Override annotation to avoid compatibility issue version < 5.0
  // Only called when in JRE 1.8+
  @IgnoreJRERequirement public CompletableFuture<T> completable() {
    return delegate.completable(); // NOTE: trace context is not propagated
  }

  // Methods from java.util.concurrent.Future
  @Override public boolean cancel(boolean mayInterruptIfRunning) {
    return delegate.cancel(mayInterruptIfRunning);
  }

  @Override public boolean isCancelled() {
    return delegate.isCancelled();
  }

  @Override public boolean isDone() {
    return delegate.isDone();
  }

  @Override public T get() throws InterruptedException, ExecutionException {
    return delegate.get();
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
    return delegate.get();
  }

  static final class TraceContextListenableFutureCallback<T>
    implements ListenableFutureCallback<T> {
    final ListenableFutureCallback<T> delegate;
    final CurrentTraceContext currentTraceContext;
    final TraceContext invocationContext;

    TraceContextListenableFutureCallback(ListenableFutureCallback<T> delegate,
      TraceContextListenableFuture<?> future) {
      this.delegate = delegate;
      this.currentTraceContext = future.currentTraceContext;
      this.invocationContext = future.invocationContext;
    }

    @Override public void onSuccess(T result) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onSuccess(result);
      }
    }

    @Override public void onFailure(Throwable ex) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onFailure(ex);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class TraceContextSuccessCallback<T> implements SuccessCallback<T> {
    final SuccessCallback<T> delegate;
    final CurrentTraceContext currentTraceContext;
    final TraceContext invocationContext;

    TraceContextSuccessCallback(SuccessCallback<T> delegate,
      TraceContextListenableFuture<?> future) {
      this.delegate = delegate;
      this.currentTraceContext = future.currentTraceContext;
      this.invocationContext = future.invocationContext;
    }

    @Override public void onSuccess(T result) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onSuccess(result);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class TraceContextFailureCallback implements FailureCallback {
    final FailureCallback delegate;
    final CurrentTraceContext currentTraceContext;
    final TraceContext invocationContext;

    TraceContextFailureCallback(FailureCallback delegate,
      TraceContextListenableFuture<?> future) {
      this.delegate = delegate;
      this.currentTraceContext = future.currentTraceContext;
      this.invocationContext = future.invocationContext;
    }

    @Override public void onFailure(Throwable ex) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onFailure(ex);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }
}

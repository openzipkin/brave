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
package brave.okhttp3;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

/**
 * Ensures callbacks run in the invocation trace context.
 *
 * <p>Note: {@link #timeout()} was added in OkHttp 3.12
 */
final class TraceContextCall implements Call {
  final Call delegate;
  final CurrentTraceContext currentTraceContext;
  final TraceContext invocationContext;

  TraceContextCall(Call delegate, CurrentTraceContext currentTraceContext,
    TraceContext invocationContext) {
    this.delegate = delegate;
    this.currentTraceContext = currentTraceContext;
    this.invocationContext = invocationContext;
  }

  @Override public void cancel() {
    delegate.cancel();
  }

  @Override public Call clone() {
    return new TraceContextCall(delegate.clone(), currentTraceContext, invocationContext);
  }

  @Override public void enqueue(Callback callback) {
    delegate.enqueue(callback != null ? new TraceContextCallback(this, callback) : null);
  }

  @Override public Response execute() throws IOException {
    try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
      return delegate.execute();
    }
  }

  @Override public boolean isCanceled() {
    return delegate.isCanceled();
  }

  @Override public boolean isExecuted() {
    return delegate.isExecuted();
  }

  @Override public Request request() {
    return delegate.request();
  }

  // Do not use @Override annotation to avoid compatibility issue version < 5.0
  public Timeout timeout() {
    return delegate.timeout();
  }

  @Override public String toString() {
    return delegate.toString();
  }

  static final class TraceContextCallback implements Callback {
    final Callback delegate;
    final CurrentTraceContext currentTraceContext;
    final TraceContext invocationContext;

    TraceContextCallback(TraceContextCall call, Callback delegate) {
      this.delegate = delegate;
      this.currentTraceContext = call.currentTraceContext;
      this.invocationContext = call.invocationContext;
    }

    @Override public void onResponse(Call call, Response response) throws IOException {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onResponse(call, response);
      }
    }

    @Override public void onFailure(Call call, IOException e) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.onFailure(call, e);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }
}

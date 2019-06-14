/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This internally adds an interceptor which ensures whatever current span exists is available via
 * {@link Tracer#currentSpanCustomizer()} and {@link Tracer#currentSpan()}
 */
// NOTE: this is not an interceptor because the current span can get lost when there's a backlog.
// This will be completely different after https://github.com/square/okhttp/issues/270
public final class TracingCallFactory implements Call.Factory {

  public static Call.Factory create(Tracing tracing, OkHttpClient ok) {
    return create(HttpTracing.create(tracing), ok);
  }

  public static Call.Factory create(HttpTracing httpTracing, OkHttpClient ok) {
    return new TracingCallFactory(httpTracing, ok);
  }

  final CurrentTraceContext currentTraceContext;
  final OkHttpClient ok;

  TracingCallFactory(HttpTracing httpTracing, OkHttpClient ok) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    if (ok == null) throw new NullPointerException("OkHttpClient == null");
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    OkHttpClient.Builder builder = ok.newBuilder();
    builder.networkInterceptors().add(0, TracingInterceptor.create(httpTracing));
    this.ok = builder.build();
  }

  @Override public Call newCall(Request request) {
    TraceContext currentSpan = currentTraceContext.get();
    OkHttpClient.Builder b = ok.newBuilder();
    if (currentSpan != null) b.interceptors().add(0, new SetParentSpanInScope(currentSpan));
    // TODO: This can hide errors at the beginning of call.execute, such as invalid host!
    return b.build().newCall(request);
  }

  /** In case a request is deferred due to a backlog, we re-apply the span that was in scope */
  class SetParentSpanInScope implements Interceptor {
    final TraceContext previous;

    SetParentSpanInScope(TraceContext previous) {
      this.previous = previous;
    }

    @Override public Response intercept(Chain chain) throws IOException {
      // using maybeScope as when there's no backlog situation the span may already be in scope
      try (Scope ws = currentTraceContext.maybeScope(previous)) {
        return chain.proceed(chain.request());
      }
    }
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Example interceptor. Use the real deal brave-instrumentation-okhttp3 in real life */
final class TracingInterceptor implements Interceptor {
  final Tracer tracer;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  TracingInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public Response intercept(Interceptor.Chain chain) throws IOException {
    RequestWrapper request = new RequestWrapper(chain.request());
    Span span = handler.handleSend(request);
    Response response = null;
    Throwable error = null;
    try (SpanInScope scope = tracer.withSpanInScope(span)) {
      return response = chain.proceed(request.build());
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      handler.handleReceive(new ResponseWrapper(response, error), span);
    }
  }

  static final class RequestWrapper extends HttpClientRequest {
    final Request delegate;
    Request.Builder builder;

    RequestWrapper(Request delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.method();
    }

    @Override public String path() {
      return delegate.url().encodedPath();
    }

    @Override public String url() {
      return delegate.url().toString();
    }

    @Override public String header(String name) {
      return delegate.header(name);
    }

    @Override public void header(String name, String value) {
      if (builder == null) builder = delegate.newBuilder();
      builder.header(name, value);
    }

    Request build() {
      return builder != null ? builder.build() : delegate;
    }
  }

  static final class ResponseWrapper extends HttpClientResponse {
    final RequestWrapper request;
    @Nullable final Response response;
    @Nullable final Throwable error;

    ResponseWrapper(@Nullable Response response, @Nullable Throwable error) {
      // intentionally not the same instance as chain.proceed, as properties may have changed
      this.request = new RequestWrapper(response.request());
      this.response = response;
      this.error = error;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public RequestWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      return response.code();
    }
  }
}

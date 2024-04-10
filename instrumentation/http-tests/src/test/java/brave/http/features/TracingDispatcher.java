/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

final class TracingDispatcher extends Dispatcher {
  final Dispatcher delegate;
  final Tracer tracer;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  TracingDispatcher(HttpTracing httpTracing, Dispatcher delegate) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing);
    this.delegate = delegate;
  }

  @Override public MockResponse dispatch(RecordedRequest req) throws InterruptedException {
    RecordedRequestWrapper request = new RecordedRequestWrapper(req);
    Span span = handler.handleReceive(request);
    MockResponse response = null;
    Throwable error = null;
    try (SpanInScope scope = tracer.withSpanInScope(span)) {
      return response = delegate.dispatch(req);
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      handler.handleSend(new MockResponseWrapper(request, response, error), span);
    }
  }

  static final class RecordedRequestWrapper extends HttpServerRequest {
    final RecordedRequest delegate;

    RecordedRequestWrapper(RecordedRequest delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.getMethod();
    }

    @Override public String path() {
      return delegate.getPath();
    }

    @Override public String url() {
      return delegate.getRequestUrl().toString();
    }

    @Override public String header(String name) {
      return delegate.getHeader(name);
    }
  }

  static final class MockResponseWrapper extends HttpServerResponse {
    final RecordedRequestWrapper request;
    final @Nullable MockResponse response;
    final @Nullable Throwable error;

    MockResponseWrapper(RecordedRequestWrapper request, @Nullable MockResponse response,
      @Nullable Throwable error) {
      this.request = request;
      this.response = response;
      this.error = error;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public RecordedRequestWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      if (response == null) return 0;
      return Integer.parseInt(response.getStatus().split(" ")[1]);
    }
  }
}

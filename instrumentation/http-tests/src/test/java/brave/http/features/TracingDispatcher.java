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
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = delegate.dispatch(req);
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      handler.handleSend(new MockResponseWrapper(request, response, error), error, span);
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
    final MockResponse response;
    final @Nullable Throwable error;

    MockResponseWrapper(RecordedRequestWrapper request, MockResponse response,
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
      return Integer.parseInt(response.getStatus().split(" ")[1]);
    }
  }
}

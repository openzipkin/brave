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
package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

final class TracingDispatcher extends Dispatcher {
  final Dispatcher delegate;
  final Tracer tracer;
  final HttpServerHandler<RecordedRequest, MockResponse> handler;
  final TraceContext.Extractor<RecordedRequest> extractor;

  TracingDispatcher(HttpTracing httpTracing, Dispatcher delegate) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new MockWebServerAdapter());
    extractor = httpTracing.tracing().propagation().extractor(RecordedRequest::getHeader);
    this.delegate = delegate;
  }

  @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
    Span span = handler.handleReceive(extractor, request);
    MockResponse response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = delegate.dispatch(request);
    } catch (InterruptedException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      handler.handleSend(response, error, span);
    }
  }

  static final class MockWebServerAdapter extends HttpServerAdapter<RecordedRequest, MockResponse> {

    @Override public String method(RecordedRequest request) {
      return request.getMethod();
    }

    @Override public String path(RecordedRequest request) {
      return request.getPath();
    }

    @Override public String url(RecordedRequest request) {
      return request.getRequestUrl().toString();
    }

    @Override public String requestHeader(RecordedRequest request, String name) {
      return request.getHeader(name);
    }

    @Override public Integer statusCode(MockResponse response) {
      return statusCodeAsInt(response);
    }

    @Override public int statusCodeAsInt(MockResponse response) {
      return Integer.parseInt(response.getStatus().split(" ")[1]);
    }
  }
}

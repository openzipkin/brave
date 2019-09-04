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
package brave.sparkjava;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import spark.ExceptionHandler;
import spark.Filter;
import spark.Request;
import spark.Response;

public final class SparkTracing {
  public static SparkTracing create(Tracing tracing) {
    return new SparkTracing(HttpTracing.create(tracing));
  }

  public static SparkTracing create(HttpTracing httpTracing) {
    return new SparkTracing(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<brave.http.HttpServerRequest, brave.http.HttpServerResponse> handler;

  SparkTracing(HttpTracing httpTracing) { // intentionally hidden constructor
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing);
  }

  public Filter before() {
    return (request, response) -> {
      Span span = handler.handleReceive(new HttpServerRequest(request));
      request.attribute(SpanInScope.class.getName(), tracer.withSpanInScope(span));
    };
  }

  public Filter afterAfter() {
    return (request, response) -> {
      Span span = tracer.currentSpan();
      if (span == null) return;
      ((SpanInScope) request.attribute(SpanInScope.class.getName())).close();
      handler.handleSend(new HttpServerResponse(response, request.requestMethod()), null, span);
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return (error, request, response) -> {
      Span span = tracer.currentSpan();
      if (span != null) {
        ((SpanInScope) request.attribute(SpanInScope.class.getName())).close();
        handler.handleSend(new HttpServerResponse(response, request.requestMethod()), error, span);
      }
      delegate.handle(error, request, response);
    };
  }

  static final class HttpServerRequest extends brave.http.HttpServerRequest {
    final Request delegate;

    HttpServerRequest(Request delegate) {
      this.delegate = delegate;
    }

    @Override public Request unwrap() {
      return delegate;
    }

    @Override public boolean parseClientIpAndPort(Span span) {
      return span.remoteIpAndPort(delegate.raw().getRemoteAddr(), delegate.raw().getRemotePort());
    }

    @Override public String method() {
      return delegate.requestMethod();
    }

    @Override public String path() {
      return delegate.pathInfo();
    }

    @Override public String url() {
      String baseUrl = delegate.url();
      if (delegate.queryString() != null && !delegate.queryString().isEmpty()) {
        return baseUrl + "?" + delegate.queryString();
      }
      return baseUrl;
    }

    @Override public String header(String name) {
      return delegate.raw().getHeader(name);
    }
  }

  static final class HttpServerResponse extends brave.http.HttpServerResponse {
    final Response delegate;
    final String method;

    HttpServerResponse(Response delegate, String method) {
      this.delegate = delegate;
      this.method = method;
    }

    @Override public Response unwrap() {
      return delegate;
    }

    @Override public String method() {
      return method;
    }

    @Override public String route() {
      return null; // Update if this or similar merged https://github.com/perwendel/spark/pull/1126
    }

    @Override public int statusCode() {
      return delegate.status();
    }
  }
}

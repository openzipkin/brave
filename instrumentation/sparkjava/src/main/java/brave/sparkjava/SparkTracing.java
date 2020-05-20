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
package brave.sparkjava;

import brave.Span;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.servlet.HttpServletRequestWrapper;
import brave.servlet.HttpServletResponseWrapper;
import spark.ExceptionHandler;
import spark.Filter;

public final class SparkTracing {
  public static SparkTracing create(Tracing tracing) {
    return new SparkTracing(HttpTracing.create(tracing));
  }

  public static SparkTracing create(HttpTracing httpTracing) {
    return new SparkTracing(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  SparkTracing(HttpTracing httpTracing) { // intentionally hidden constructor
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpServerHandler.create(httpTracing);
  }

  public Filter before() {
    return (request, response) -> {
      // Add servlet attribute "http.route" if this or similar is merged:
      // https://github.com/perwendel/spark/pull/1126
      Span span = handler.handleReceive(HttpServletRequestWrapper.create(request.raw()));
      request.attribute(Span.class.getName(), span);
      request.attribute(Scope.class.getName(), currentTraceContext.newScope(span.context()));
    };
  }

  public Filter afterAfter() {
    return (req, res) -> {
      Span span = req.attribute(Span.class.getName());
      if (span == null) return;
      HttpServerResponse response = HttpServletResponseWrapper.create(req.raw(), res.raw(), null);
      handler.handleSend(response, span);
      ((Scope) req.attribute(Scope.class.getName())).close();
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return (error, req, res) -> {
      try {
        delegate.handle(error, req, res);
      } finally {
        Span span = req.attribute(Span.class.getName());
        if (span != null) {
          HttpServerResponse response =
            HttpServletResponseWrapper.create(req.raw(), res.raw(), error);
          handler.handleSend(response, span);
          req.raw().removeAttribute(Span.class.getName()); // prevent double-processing
          ((Scope) req.attribute(Scope.class.getName())).close();
        }
      }
    };
  }
}

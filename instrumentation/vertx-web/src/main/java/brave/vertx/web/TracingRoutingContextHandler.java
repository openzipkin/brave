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
package brave.vertx.web;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h3>Why not rely on {@code context.request().endHandler()} to finish a span?</h3>
 * <p>There can be only one {@link HttpServerRequest#endHandler(Handler) end handler}. We can't
 * rely on {@code endHandler()} as a user can override it in their route. If they did, we'd leak an
 * unfinished span. For this reason, we speculatively use both an end handler and an end header
 * handler.
 *
 * <p>The hint that we need to re-attach the headers handler on re-route came from looking at
 * {@code TracingHandler} in https://github.com/opentracing-contrib/java-vertx-web
 */
final class TracingRoutingContextHandler implements Handler<RoutingContext> {
  static final Getter<HttpServerRequest, String> GETTER = new Getter<HttpServerRequest, String>() {
    @Override public String get(HttpServerRequest carrier, String key) {
      return carrier.getHeader(key);
    }

    @Override public String toString() {
      return "HttpServerRequest::getHeader";
    }
  };

  final Tracer tracer;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;
  final TraceContext.Extractor<HttpServerRequest> extractor;

  TracingRoutingContextHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    serverHandler = HttpServerHandler.create(httpTracing, new VertxHttpServerAdapter());
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  @Override public void handle(RoutingContext context) {
    TracingHandler tracingHandler = context.get(TracingHandler.class.getName());
    if (tracingHandler != null) { // then we already have a span
      if (!context.failed()) { // re-routed, so re-attach the end handler
        context.addHeadersEndHandler(tracingHandler);
      }
      context.next();
      return;
    }

    Span span = serverHandler.handleReceive(extractor, context.request());
    TracingHandler handler = new TracingHandler(context, span);
    context.put(TracingHandler.class.getName(), handler);
    context.addHeadersEndHandler(handler);

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      context.next();
    }
  }

  class TracingHandler implements Handler<Void> {
    final RoutingContext context;
    final Span span;
    final AtomicBoolean finished = new AtomicBoolean();

    TracingHandler(RoutingContext context, Span span) {
      this.context = context;
      this.span = span;
    }

    @Override public void handle(Void aVoid) {
      if (!finished.compareAndSet(false, true)) return;
      VertxHttpServerAdapter.setCurrentMethodAndPath(
        context.request().rawMethod(),
        context.currentRoute().getPath()
      );
      try {
        serverHandler.handleSend(context.response(), context.failure(), span);
      } finally {
        VertxHttpServerAdapter.setCurrentMethodAndPath(null, null);
      }
    }
  }
}

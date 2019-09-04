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
import io.vertx.core.Handler;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h3>Why not rely on {@code context.request().endHandler()} to finish a span?</h3>
 * <p>There can be only one {@link io.vertx.core.http.HttpServerRequest#endHandler(Handler) end
 * handler}. We can't rely on {@code endHandler()} as a user can override it in their route. If they
 * did, we'd leak an unfinished span. For this reason, we speculatively use both an end handler and
 * an end header handler.
 *
 * <p>The hint that we need to re-attach the headers handler on re-route came from looking at
 * {@code TracingHandler} in https://github.com/opentracing-contrib/java-vertx-web
 */
final class TracingRoutingContextHandler implements Handler<RoutingContext> {
  final Tracer tracer;
  final HttpServerHandler<brave.http.HttpServerRequest, brave.http.HttpServerResponse> handler;

  TracingRoutingContextHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing);
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

    Span span = handler.handleReceive(new HttpServerRequest(context.request()));
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
      handler.handleSend(new HttpServerResponse(context), context.failure(), span);
    }
  }

  static final class HttpServerRequest extends brave.http.HttpServerRequest {
    final io.vertx.core.http.HttpServerRequest delegate;

    HttpServerRequest(io.vertx.core.http.HttpServerRequest delegate) {
      this.delegate = delegate;
    }

    @Override public io.vertx.core.http.HttpServerRequest unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.rawMethod();
    }

    @Override public String path() {
      return delegate.path();
    }

    @Override public String url() {
      return delegate.absoluteURI();
    }

    @Override public String header(String name) {
      return delegate.headers().get(name);
    }

    @Override public boolean parseClientIpAndPort(Span span) {
      SocketAddress addr = delegate.remoteAddress();
      return span.remoteIpAndPort(addr.host(), addr.port());
    }
  }

  static final class HttpServerResponse extends brave.http.HttpServerResponse {
    final io.vertx.core.http.HttpServerResponse delegate;
    final String method, httpRoute;

    HttpServerResponse(RoutingContext context) {
      this.delegate = context.response();
      this.method = context.request().rawMethod();
      String httpRoute = context.currentRoute().getPath();
      this.httpRoute = httpRoute != null ? httpRoute : "";
    }

    @Override public io.vertx.core.http.HttpServerResponse unwrap() {
      return delegate;
    }

    @Override public int statusCode() {
      return delegate.getStatusCode();
    }

    @Override public String method() {
      return method;
    }

    @Override public String route() {
      return httpRoute;
    }
  }
}

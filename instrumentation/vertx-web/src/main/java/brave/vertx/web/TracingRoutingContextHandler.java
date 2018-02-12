package brave.vertx.web;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import zipkin2.Endpoint;

/**
 * <h3>Why not rely on {@code context.request().endHandler()} to finish a span?</h3>
 * <p>There can be only one {@link HttpServerRequest#endHandler(Handler) end handler}. We can't rely
 * on {@code endHandler()} as a user can override it in their route. If they did, we'd leak an
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
    serverHandler = HttpServerHandler.create(httpTracing, new Adapter());
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

    // When a route ends a request directly, this will finish the span
    context.request().endHandler(handler);
    // When a route overwrites the above endHandler, this will finish the span
    context.addHeadersEndHandler(handler);

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      context.next();
    }
  }

  class TracingHandler implements Handler<Void> {
    final RoutingContext context;
    final Span span;

    TracingHandler(RoutingContext context, Span span) {
      this.context = context;
      this.span = span;
    }

    @Override public void handle(Void aVoid) {
      if (!context.request().isEnded()) return;
      serverHandler.handleSend(context.response(), context.failure(), span);
    }
  }

  static final class Adapter extends HttpServerAdapter<HttpServerRequest, HttpServerResponse> {
    @Override public String method(HttpServerRequest request) {
      return request.method().name();
    }

    @Override public String path(HttpServerRequest request) {
      return request.path();
    }

    @Override public String url(HttpServerRequest request) {
      return request.absoluteURI();
    }

    @Override public String requestHeader(HttpServerRequest request, String name) {
      return request.headers().get(name);
    }

    @Override public Integer statusCode(HttpServerResponse response) {
      return response.getStatusCode();
    }

    @Override
    public boolean parseClientAddress(HttpServerRequest req, Endpoint.Builder builder) {
      if (super.parseClientAddress(req, builder)) return true;
      SocketAddress addr = req.remoteAddress();
      if (builder.parseIp(addr.host())) {
        builder.port(addr.port());
        return true;
      }
      return false;
    }
  }
}

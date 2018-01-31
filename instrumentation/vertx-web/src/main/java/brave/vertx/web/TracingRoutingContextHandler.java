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
 * Idea for how to address re-route was from {@code TracingHandler} in https://github.com/opentracing-contrib/java-vertx-web
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
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;
  final TraceContext.Extractor<HttpServerRequest> extractor;

  TracingRoutingContextHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, ADAPTER);
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  @Override public void handle(RoutingContext context) {
    boolean newRequest = false;
    Span span = context.get(Span.class.getName());
    if (span == null) {
      newRequest = true;
      span = handler.handleReceive(extractor, context.request());
      context.put(Span.class.getName(), span);
    }

    if (newRequest || !context.failed()) { // re-routed, so re-attach the end handler
      // Note: In Brave, finishing a client span after headers sent is normal.
      context.addHeadersEndHandler(finishHttpSpan(context, span));
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      context.next();
    } catch (RuntimeException | Error e) {
      handler.handleSend(null, e, span);
      throw e;
    }
  }

  Handler<Void> finishHttpSpan(RoutingContext context, Span span) {
    return v -> handler.handleSend(context.response(), context.failure(), span);
  }

  static final HttpServerAdapter<HttpServerRequest, HttpServerResponse> ADAPTER =
      new HttpServerAdapter<HttpServerRequest, HttpServerResponse>() {
        @Override public String method(HttpServerRequest request) {
          return request.method().name();
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
      };
}

package brave.vertx.web;

import brave.Tracing;
import brave.http.HttpTracing;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public final class VertxWebTracing {
  public static VertxWebTracing create(Tracing tracing) {
    return new VertxWebTracing(HttpTracing.create(tracing));
  }

  public static VertxWebTracing create(HttpTracing httpTracing) {
    return new VertxWebTracing(httpTracing);
  }

  final Handler<RoutingContext> routingContextHandler;

  VertxWebTracing(HttpTracing httpTracing) {
    routingContextHandler = new TracingRoutingContextHandler(httpTracing);
  }

  /**
   * Returns a routing context handler that traces {@link HttpServerRequest} messages.
   *
   * <p>Ensure you install this both as a handler and an failure handler, ordered before routes.
   * <pre>{@code
   * routingContextHandler = vertxWebTracing.routingContextHandler();
   * router.route()
   *       .order(-1) // applies before routes
   *       .handler(routingContextHandler)
   *       .failureHandler(routingContextHandler);
   * }</pre>
   */
  public Handler<RoutingContext> routingContextHandler() {
    return routingContextHandler;
  }
}

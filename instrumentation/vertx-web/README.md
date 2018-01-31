# brave-instrumentation-vertx-web

This module contains a routing context handler for [Vert.x Web](http://vertx.io/docs/vertx-web/js/)
This extracts trace state from incoming requests. Then, it reports to
Zipkin how long each request takes, along with relevant tags like the
http url. Register this as an failure handler to ensure any errors are
also sent to Zipkin.

To enable tracing you need to set `order`, `handler` and `failureHandler`
hooks:
```java
vertxWebTracing = VertxWebTracing.create(httpTracing);
routingContextHandler = vertxWebTracing.routingContextHandler();
router.route()
      .order(-1) // applies before routes
      .handler(routingContextHandler)
      .failureHandler(routingContextHandler);

// any routes you add are now traced, such as the below
router.route("/foo").handler(ctx -> {
    ctx.response().end("bar");
});
```
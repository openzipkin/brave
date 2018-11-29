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
The exception is that the vert.x tracing order cannot be set to -1 
when the application needs to handle requests such as POST 
and needs to place the vert.x traing after
 the `router.route().Handler(BodyHandler.create())`.<br/>
 (例外，当应用需要处理POST等请求，需要把vert.x traing 放到
 `router.route().handler(BodyHandler.create())`之后，vert.x tracing order 不能设置为-1)
```java
router.route().handler(BodyHandler.create());
Handler<RoutingContext> routingContextHandler = VertxWebTracing.create(httpTracing).routingContextHandler();
router.route()
  .order(1) // applies before routes
  .handler(routingContextHandler)
  .failureHandler(routingContextHandler);
```
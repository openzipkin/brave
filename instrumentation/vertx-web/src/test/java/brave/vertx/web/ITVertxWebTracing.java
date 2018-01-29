package brave.vertx.web;

import brave.http.ITHttpServer;
import brave.propagation.ExtraFieldPropagation;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ITVertxWebTracing extends ITHttpServer {
  Vertx vertx;
  HttpServer server;
  volatile int port;

  @Override protected void init() throws Exception {
    stop();
    vertx = Vertx.vertx(new VertxOptions());

    Router router = Router.router(vertx);
    router.route("/foo").handler(ctx -> {
      ctx.response().end("bar");
    });
    router.route("/async").handler(ctx -> {
      ctx.request().endHandler(v -> ctx.response().end("bar"));
    });
    router.route("/reroute").handler(ctx -> {
      ctx.reroute("/foo");
    });
    router.route("/extra").handler(ctx -> {
      ctx.response().end(ExtraFieldPropagation.get(EXTRA_KEY));
    });
    router.route("/badrequest").handler(ctx -> {
      ctx.response().setStatusCode(400).end();
    });
    router.route("/child").handler(ctx -> {
      httpTracing.tracing().tracer().nextSpan().name("child").start().finish();
      ctx.response().end("happy");
    });
    router.route("/exception").handler(ctx -> {
      ctx.fail(new Exception());
    });
    router.route("/exceptionAsync").handler(ctx -> {
      ctx.request().endHandler(v -> ctx.fail(new Exception()));
    });


    Handler<RoutingContext> routingContextHandler =
        VertxWebTracing.create(httpTracing).routingContextHandler();
    router.route()
        .order(-1).handler(routingContextHandler)
        .failureHandler(routingContextHandler);

    server = vertx.createHttpServer(new HttpServerOptions().setPort(0).setHost("localhost"));

    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(router::accept).listen(async -> {
      port = async.result().actualPort();
      latch.countDown();
    });

    assertThat(latch.await(10, TimeUnit.SECONDS))
        .withFailMessage("server didn't start")
        .isTrue();
  }

  // makes sure we don't accidentally rewrite the incoming http path
  @Test public void handlesReroute() throws Exception {
    get("/reroute");

    assertThat(spans)
        .hasSize(1)
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry("http.path", "/reroute"));
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  @After public void stop() throws Exception {
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close(ar -> {
        latch.countDown();
      });
      latch.await(10, TimeUnit.SECONDS);
    }
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close(ar -> {
        latch.countDown();
      });
      latch.await(10, TimeUnit.SECONDS);
      vertx = null;
    }
  }
}

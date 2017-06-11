package com.github.kristofa.brave.sparkjava;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import spark.ExceptionHandler;
import spark.Request;
import spark.Response;
import spark.Spark;

public class ITBraveTracingTest extends ITHttpServer {

  /**
   * After filters executed before NotFoundRoute.
   * Can't add 404 code after server span has been setServerSend.
   * May be throw a NotFoundException and process it in ExceptionHandler.
   */
  @Override
  @Test
  public void addsStatusCodeWhenNotOk() throws Exception {
    throw new AssumptionViolatedException("Filters run before NotFoundRoute can be determined");
  }

  @Override
  protected void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception {
    stop();

    BraveTracing tracing =
        BraveTracing.builder(brave).spanNameProvider(spanNameProvider).build();
    Spark.before(tracing.before());
    Spark.exception(Exception.class, tracing.exception(new ExceptionHandler() {
      @Override public void handle(Exception exception, Request request, Response response) {
        response.body("exception");
      }
    }));
    Spark.afterAfter(tracing.afterAfter());

    Spark.get("/foo", (req, res) -> "bar");
    Spark.get("/child", (req, res) -> {
      brave.localTracer().startNewSpan("child", "child");
      brave.localTracer().finishSpan();
      return "happy";
    });
    Spark.get("/disconnect", (req, res) -> {
      throw new Exception();
    });

    Spark.awaitInitialization();
  }

  @Override
  protected String url(String path) {//default port 4567
    return "http://localhost:4567" + path;
  }

  /**
   * Spark stop asynchronously but share one class Instance,
   * so AddressAlreadyUsed Exception may happen.
   * See:https://github.com/perwendel/spark/issues/705 .
   * Just sleep 1 second to avoid this happens,
   * after Spark.awaitStopped add,I will fix it.
   */
  @After
  public void stop() throws Exception {
    Spark.stop();
    Thread.sleep(1000);
  }
}

package brave.sparkjava;

import brave.Tracing;
import brave.propagation.ExtraFieldPropagation;
import spark.Spark;
import spark.servlet.SparkApplication;

import static brave.test.http.ITHttp.EXTRA_KEY;

public class TestApplication implements SparkApplication {
  @Override public void init() {
    Spark.options("/", (req, res) -> "");
    Spark.get("/foo", (req, res) -> "bar");
    Spark.get("/extra", (req, res) -> ExtraFieldPropagation.get(EXTRA_KEY));
    Spark.get("/badrequest", (req, res) -> {
      res.status(400);
      return res;
    });
    Spark.get("/child", (req, res) -> {
      Tracing.currentTracer().nextSpan().name("child").start().finish();
      return "happy";
    });
    Spark.get("/exception", (req, res) -> {
      throw new Exception();
    });

    // TODO: we need matchUri: https://github.com/perwendel/spark/issues/959
    //Spark.get("/items/:itemId", (request, response) -> request.params(":itemId"));
    //Spark.path("/nested", () ->
    //    Spark.get("/items/:itemId", (request, response) -> request.params(":itemId"))
    //);
  }
}

package brave.sparkjava;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.sampler.Sampler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.servlet.SparkApplication;
import spark.servlet.SparkFilter;

import static javax.servlet.DispatcherType.REQUEST;

public class SparkBenchmarks extends HttpServerBenchmarks {

  public static class NotTraced implements SparkApplication {
    @Override
    public void init() {
      Spark.get("/nottraced", (Request request, Response response) -> "hello world");
    }
  }

  public static class Unsampled implements SparkApplication {
    SparkTracing sparkTracing = SparkTracing.create(
        Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).spanReporter(s -> {}).build()
    );

    @Override
    public void init() {
      Spark.before(sparkTracing.before());
      Spark.get("/unsampled", (Request request, Response response) -> "hello world");
      Spark.afterAfter(sparkTracing.afterAfter());
    }
  }

  public static class Traced implements SparkApplication {
    SparkTracing sparkTracing = SparkTracing.create(
        Tracing.newBuilder().spanReporter(s -> {}).build()
    );

    @Override
    public void init() {
      Spark.before(sparkTracing.before());
      Spark.get("/traced", (Request request, Response response) -> "hello world");
      Spark.afterAfter(sparkTracing.afterAfter());
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    servletBuilder
        .addFilter(new FilterInfo("NotTraced", SparkFilter.class)
            .addInitParam("applicationClass", NotTraced.class.getName()))
        .addFilterUrlMapping("NotTraced", "/*", REQUEST)
        .addFilter(new FilterInfo("Unsampled", SparkFilter.class)
            .addInitParam("applicationClass", Unsampled.class.getName()))
        .addFilterUrlMapping("Unsampled", "/unsampled", REQUEST)
        .addFilter(new FilterInfo("Traced", SparkFilter.class)
            .addInitParam("applicationClass", Traced.class.getName()))
        .addFilterUrlMapping("Traced", "/traced", REQUEST);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + SparkBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}

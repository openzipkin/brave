/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.sparkjava;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.propagation.B3Propagation;
import brave.baggage.BaggagePropagation;
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
import zipkin2.reporter.Reporter;

import static brave.baggage.BaggagePropagationBenchmarks.BAGGAGE_FIELD;
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
      Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).spanReporter(Reporter.NOOP).build()
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
      Tracing.newBuilder().spanReporter(Reporter.NOOP).build()
    );

    @Override
    public void init() {
      Spark.before(sparkTracing.before());
      Spark.get("/traced", (Request request, Response response) -> "hello world");
      Spark.afterAfter(sparkTracing.afterAfter());
    }
  }

  public static class TracedBaggage implements SparkApplication {
    SparkTracing sparkTracing = SparkTracing.create(
      Tracing.newBuilder()
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .addRemoteField(BAGGAGE_FIELD).build())
        .spanReporter(Reporter.NOOP).build()
    );

    @Override
    public void init() {
      Spark.before(sparkTracing.before());
      Spark.get("/tracedBaggage", (Request request, Response response) -> {
        BAGGAGE_FIELD.updateValue("FO");
        return "hello world";
      });
      Spark.afterAfter(sparkTracing.afterAfter());
    }
  }

  public static class Traced128 implements SparkApplication {
    SparkTracing sparkTracing = SparkTracing.create(
      Tracing.newBuilder().traceId128Bit(true).spanReporter(Reporter.NOOP).build()
    );

    @Override
    public void init() {
      Spark.before(sparkTracing.before());
      Spark.get("/traced128", (Request request, Response response) -> "hello world");
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
      .addFilterUrlMapping("Traced", "/traced", REQUEST)
      .addFilter(new FilterInfo("TracedBaggage", SparkFilter.class)
        .addInitParam("applicationClass", TracedBaggage.class.getName()))
      .addFilterUrlMapping("TracedBaggage", "/tracedBaggage", REQUEST)
      .addFilter(new FilterInfo("Traced128", SparkFilter.class)
        .addInitParam("applicationClass", Traced128.class.getName()))
      .addFilterUrlMapping("Traced128", "/traced128", REQUEST);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + SparkBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

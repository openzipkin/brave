/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.jersey.server;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.http.HttpTracing;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import io.undertow.servlet.api.DeploymentInfo;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import org.glassfish.jersey.servlet.ServletContainer;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.reporter.Reporter;

import static io.undertow.servlet.Servlets.servlet;
import static java.util.Arrays.asList;

public class JerseyServerBenchmarks extends HttpServerBenchmarks {
  @Path("")
  public static class Resource {
    @GET @Produces("text/plain; charset=UTF-8") public String get() {
      // noop if not configured
      ExtraFieldPropagation.set("country-code", "FO");
      return "hello world";
    }
  }

  @ApplicationPath("/nottraced")
  public static class App extends Application {
    @Override public Set<Object> getSingletons() {
      return Collections.singleton(new Resource());
    }
  }

  @ApplicationPath("/unsampled")
  public static class Unsampled extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder()
          .sampler(Sampler.NEVER_SAMPLE)
          .spanReporter(Reporter.NOOP)
          .build())
      )));
    }
  }

  @ApplicationPath("/traced")
  public static class TracedApp extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder().spanReporter(Reporter.NOOP).build())
      )));
    }
  }

  @ApplicationPath("/tracedextra")
  public static class TracedExtraApp extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder()
          .propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .addField("x-vcap-request-id")
            .addPrefixedFields("baggage-", asList("country-code", "user-id"))
            .build()
          )
          .spanReporter(Reporter.NOOP)
          .build())
      )));
    }
  }

  @ApplicationPath("/traced128")
  public static class Traced128App extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder()
          .traceId128Bit(true)
          .spanReporter(Reporter.NOOP)
          .build())
      )));
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    servletBuilder.addServlets(
      servlet("Unsampled", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", Unsampled.class.getName())
        .addMapping("/unsampled"),
      servlet("Traced", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", TracedApp.class.getName())
        .addMapping("/traced"),
      servlet("TracedExtra", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", TracedExtraApp.class.getName())
        .addMapping("/tracedextra"),
      servlet("Traced128", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", Traced128App.class.getName())
        .addMapping("/traced128"),
      servlet("App", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", App.class.getName())
        .addMapping("/*")
    );
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + JerseyServerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

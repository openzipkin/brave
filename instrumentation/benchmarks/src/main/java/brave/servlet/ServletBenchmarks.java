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
package brave.servlet;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import java.io.IOException;
import java.util.Arrays;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.reporter.Reporter;

import static javax.servlet.DispatcherType.REQUEST;

public class ServletBenchmarks extends HttpServerBenchmarks {

  static class HelloServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
      // noop if not configured
      ExtraFieldPropagation.set("country-code", "FO");
      resp.addHeader("Content-Type", "text/plain; charset=UTF-8");
      resp.getWriter().println("hello world");
    }
  }

  public static class Unsampled extends ForwardingTracingFilter {
    public Unsampled() {
      super(TracingFilter.create(
        Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).spanReporter(Reporter.NOOP).build()
      ));
    }
  }

  public static class Traced extends ForwardingTracingFilter {
    public Traced() {
      super(TracingFilter.create(Tracing.newBuilder().spanReporter(Reporter.NOOP).build()));
    }
  }

  public static class TracedExtra extends ForwardingTracingFilter {
    public TracedExtra() {
      super(TracingFilter.create(Tracing.newBuilder()
        .propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .addField("x-vcap-request-id")
          .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
          .build()
        )
        .spanReporter(Reporter.NOOP)
        .build()));
    }
  }

  public static class Traced128 extends ForwardingTracingFilter {
    public Traced128() {
      super(TracingFilter.create(
        Tracing.newBuilder().traceId128Bit(true).spanReporter(Reporter.NOOP).build()));
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    addFilterMappings(servletBuilder);
    servletBuilder.addServlets(
      Servlets.servlet("HelloServlet", HelloServlet.class).addMapping("/*")
    );
  }

  public static void addFilterMappings(DeploymentInfo servletBuilder) {
    servletBuilder.addFilter(new FilterInfo("Unsampled", Unsampled.class))
      .addFilterUrlMapping("Unsampled", "/unsampled", REQUEST)
      .addFilter(new FilterInfo("Traced", Traced.class))
      .addFilterUrlMapping("Traced", "/traced", REQUEST)
      .addFilter(new FilterInfo("TracedExtra", TracedExtra.class))
      .addFilterUrlMapping("TracedExtra", "/tracedextra", REQUEST)
      .addFilter(new FilterInfo("Traced128", Traced128.class))
      .addFilterUrlMapping("Traced128", "/traced128", REQUEST);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + ServletBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }

  static class ForwardingTracingFilter implements Filter {
    final Filter delegate;

    ForwardingTracingFilter(Filter delegate) {
      this.delegate = delegate;
    }

    @Override public void init(FilterConfig filterConfig) {
    }

    @Override public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
      FilterChain filterChain) throws IOException, ServletException {
      delegate.doFilter(servletRequest, servletResponse, filterChain);
    }

    @Override public void destroy() {
    }
  }
}

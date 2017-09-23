package brave.servlet;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.sampler.Sampler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import java.io.IOException;
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

  @Override protected void init(DeploymentInfo servletBuilder) {
    servletBuilder.addFilter(new FilterInfo("Unsampled", Unsampled.class))
        .addFilterUrlMapping("Unsampled", "/unsampled", REQUEST)
        .addFilter(new FilterInfo("Traced", Traced.class))
        .addFilterUrlMapping("Traced", "/traced", REQUEST)
        .addServlets(Servlets.servlet("HelloServlet", HelloServlet.class).addMapping("/*"));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + ServletBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }

  static class ForwardingTracingFilter implements Filter {
    final Filter delegate;

    public ForwardingTracingFilter(Filter delegate) {
      this.delegate = delegate;
    }

    @Override public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      delegate.doFilter(servletRequest, servletResponse, filterChain);
    }

    @Override public void destroy() {
    }
  }
}

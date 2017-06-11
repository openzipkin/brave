package brave.http;

import brave.Tracer;
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
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITServlet25Container extends ITServletContainer {

  static class StatusServlet extends HttpServlet {
    final int status;

    StatusServlet(int status) {
      this.status = status;
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      resp.setStatus(status);
    }
  }

  static class ChildServlet extends HttpServlet {
    final Tracer tracer;

    ChildServlet(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      tracer.nextSpan().name("child").start().finish();
      resp.setStatus(200);
    }
  }

  static class ExceptionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      throw new IOException(); // null exception message!
    }
  }

  Filter userFilter = new Filter() {
    @Override public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      String traceId = currentTraceContext.get().traceIdString();
      ((HttpServletResponse) response).setHeader("my-id", traceId);
      chain.doFilter(request, response);
    }

    @Override public void destroy() {
    }
  };

  @Test public void currentSpanVisibleToOtherFilters() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
      String idString = spans.iterator().next().idString();
      assertThat(idString).startsWith(response.header("my-id"));
    }
  }

  @Override
  public void init(ServletContextHandler handler) {
    // add servlets for the test resource
    handler.addServlet(new ServletHolder(new StatusServlet(200)), "/foo");
    handler.addServlet(new ServletHolder(new StatusServlet(400)), "/badrequest");
    handler.addServlet(new ServletHolder(new ChildServlet(httpTracing)), "/child");
    handler.addServlet(new ServletHolder(new ExceptionServlet()), "/exception");

    // add the trace filter
    addFilter(handler, newTracingFilter());
    // add a user filter
    addFilter(handler, userFilter);
  }

  protected abstract Filter newTracingFilter();

  protected abstract void addFilter(ServletContextHandler handler, Filter filter);
}

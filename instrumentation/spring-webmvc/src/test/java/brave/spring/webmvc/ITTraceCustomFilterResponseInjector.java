package brave.spring.webmvc;

import brave.Tracing;
import brave.propagation.TraceContext;
import brave.servlet.TracingFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.filter.GenericFilterBean;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTraceCustomFilterResponseInjector extends ITSpringServlet {

  @Override List<Class<?>> configurationClasses() {
    // We aren't configuring the MVC filter as we are tracing with the servlet filter
    // Until we change things, if we add AsyncHandlerInterceptorConfig.class there will be double spans!
    return Arrays.asList(CustomRestController.class);
  }

  @Override void init(ServletContextHandler handler) {
    super.init(handler);
    // add the trace filter
    handler.getServletContext()
        .addFilter("tracingFilter", TracingFilter.create(httpTracing))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

    // add a user filter
    handler.getServletContext()
        .addFilter("myFilter", new HttpResponseInjectingTraceFilter(httpTracing.tracing()))
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  @Controller
  static class CustomRestController {
    @RequestMapping("/foo")
    public ResponseEntity<String> headers() {
      return new ResponseEntity<>("bar", HttpStatus.OK);
    }
  }

  static class HttpResponseInjectingTraceFilter extends GenericFilterBean {
    final Tracing tracing;

    HttpResponseInjectingTraceFilter(Tracing tracing) {
      this.tracing = tracing;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      TraceContext currentSpan = tracing.currentTraceContext().get();
      response.addHeader("traceId", currentSpan.traceIdString());
      filterChain.doFilter(request, response);
    }
  }

  @Test
  public void should_inject_trace_and_span_ids_in_response_headers() throws Exception {
    Response response = get("/foo");
    assertThat(response.body().string())
        .isEqualTo("bar"); // MVC got the request

    Span span = takeSpan();
    // the servlet filter could read the trace ID
    assertThat(response.header("traceId"))
        .isEqualTo(span.traceId());
  }
}

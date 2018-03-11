package brave.spring.webmvc;

import brave.http.HttpTracing;
import brave.servlet.TracingFilter;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.springframework.context.ApplicationContext;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

/**
 * Similar to {@link TracingFilter}, except that it initializes from Spring.
 *
 * <p> {@link org.springframework.web.filter.DelegatingFilterProxy} is similar, but it uses
 * volatile references as it allows lazy initialization from doGet. This filter cannot do that
 * anyway because {@code ServletRequest.getServletContext()} was added after servlet 2.5!
 */
public final class DelegatingTracingFilter implements Filter {

  Filter delegate; // servlet ensures create is directly followed by init, so no need for volatile

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Filter tracingFilter = delegate;
    if (tracingFilter == null) { // don't break on initialization error.
      chain.doFilter(request, response);
    } else {
      tracingFilter.doFilter(request, response, chain);
    }
  }

  @Override public void init(FilterConfig filterConfig) {
    ApplicationContext ctx = getRequiredWebApplicationContext(filterConfig.getServletContext());
    HttpTracing httpTracing = WebMvcRuntime.get().httpTracing(ctx);
    delegate = TracingFilter.create(httpTracing);
  }

  @Override public void destroy() {
    // TracingFilter is stateless, so nothing to destroy
  }
}

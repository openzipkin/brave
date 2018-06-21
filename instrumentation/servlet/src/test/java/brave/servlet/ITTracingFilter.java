package brave.servlet;

import brave.test.http.ITServlet3Container;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class ITTracingFilter extends ITServlet3Container {

  @Override
  protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }

  @Override
  protected void addFilter(ServletContextHandler handler, Filter filter) {
    FilterRegistration.Dynamic filterRegistration =
        handler.getServletContext().addFilter(filter.getClass().getSimpleName(), filter);
    filterRegistration.setAsyncSupported(true);
    // isMatchAfter=true is required for async tests to pass!
    filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}

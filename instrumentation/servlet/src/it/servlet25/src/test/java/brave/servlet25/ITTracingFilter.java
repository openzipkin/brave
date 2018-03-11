package brave.servlet25;

import brave.test.http.ITServlet25Container;
import brave.servlet.TracingFilter;
import java.util.EnumSet;
import javax.servlet.Filter;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class ITTracingFilter extends ITServlet25Container {

  @Override protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }

  @Override protected void addFilter(ServletContextHandler handler, Filter filter) {
    handler.addFilter(new FilterHolder(filter), "/*", EnumSet.allOf(DispatcherType.class));
  }
}

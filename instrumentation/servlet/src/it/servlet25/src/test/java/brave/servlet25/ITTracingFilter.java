package brave.servlet25;

import brave.http.ITServlet25Container;
import brave.servlet.TracingFilter;
import javax.servlet.Filter;

public class ITTracingFilter extends ITServlet25Container {

  @Override protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }
}

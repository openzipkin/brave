package brave.servlet25;

import brave.servlet.TracingFilter;
import javax.servlet.Filter;
import brave.test.http.ITServlet25Container;

public class ITTracingFilter extends ITServlet25Container {

  @Override protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }
}

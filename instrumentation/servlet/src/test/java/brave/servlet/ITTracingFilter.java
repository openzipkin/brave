package brave.servlet;

import brave.test.http.ITServlet3Container;
import javax.servlet.Filter;

public class ITTracingFilter extends ITServlet3Container {

  @Override protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }
}

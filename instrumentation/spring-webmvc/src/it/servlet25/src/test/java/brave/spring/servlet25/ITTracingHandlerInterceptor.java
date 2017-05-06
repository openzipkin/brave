package brave.spring.servlet25;

import brave.servlet.TracingFilter;
import org.junit.Test;

public class ITTracingHandlerInterceptor extends brave.spring.webmvc.ITTracingHandlerInterceptor {

  @Test(expected = AssertionError.class)
  @Override public void async() throws Exception {
    super.async();
  }

  /**
   * {@code HttpServletRequest.getStatus()} was not added until Servlet 3.0. Unless we look
   * reflectively for types that implement this even in Servlet 2.5 (like jetty and tomcat), this
   * will not show the error code. Note that {@link TracingFilter} deals with this directlys.
   */
  @Test(expected = AssertionError.class)
  @Override public void addsStatusCode_badRequest() throws Exception {
    super.addsStatusCode_badRequest();
  }
}

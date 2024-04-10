/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.servlet;

import brave.test.jakarta.http.ITServlet5Container;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.UnavailableException;
import java.util.EnumSet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Disabled;

class ITTracingFilter extends ITServlet5Container {

  @Override protected Filter newTracingFilter() {
    return TracingFilter.create(httpTracing);
  }

  @Override protected void addFilter(ServletContextHandler handler, Filter filter) {
    FilterRegistration.Dynamic filterRegistration =
        handler.getServletContext().addFilter(filter.getClass().getSimpleName(), filter);
    filterRegistration.setAsyncSupported(true);
    // isMatchAfter=true is required for async tests to pass!
    filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  /**
   * Jetty 9.4.28.v20200408 {@link HttpChannelState} {@code #sendError(Throwable)} ignores the
   * attribute {@link RequestDispatcher#ERROR_STATUS_CODE}.
   *
   * <p>It might seem we should use {@link #NOT_READY_UE} instead, but Jetty {@link
   * ServletHolder#handle(Request, ServletRequest, ServletResponse)} catches {@link
   * UnavailableException} and swaps internally to a servlet instance that doesn't set the exception
   * cause.
   */
  @Disabled("We can't set the error code for an uncaught exception with jetty-servlet")
  @Override public void httpStatusCodeSettable_onUncaughtException() {
  }

  @Disabled("We can't set the error code for an uncaught exception with jetty-servlet")
  @Override public void httpStatusCodeSettable_onUncaughtException_async() {
  }
}

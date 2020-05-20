/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.servlet;

import brave.test.http.ITServlet3Container;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.UnavailableException;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Ignore;

public class ITTracingFilter extends ITServlet3Container {

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
  @Ignore("We can't set the error code for an uncaught exception with jetty-servlet")
  @Override public void httpStatusCodeSettable_onUncaughtException() {
  }

  @Ignore("We can't set the error code for an uncaught exception with jetty-servlet")
  @Override public void httpStatusCodeSettable_onUncaughtException_async() {
  }
}

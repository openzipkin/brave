/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

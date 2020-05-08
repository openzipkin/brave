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
package brave.sparkjava;

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import brave.test.http.Jetty9ServerController;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Ignore;
import spark.servlet.SparkFilter;

public class ITTracingFilter extends ITServletContainer {
  public ITTracingFilter() {
    super(new Jetty9ServerController());
  }

  @Override public void init(ServletContextHandler handler) {
    handler.getServletContext()
      .addFilter("tracingFilter", TracingFilter.create(httpTracing))
      .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

    Dynamic sparkFilter = handler.getServletContext().addFilter("sparkFilter", new SparkFilter());
    sparkFilter.setInitParameter("applicationClass", TestApplication.class.getName());
    sparkFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  @Override @Ignore("TODO: make a spark.ExceptionMapper that adds the \"error\" request property")
  public void errorTag_exceptionOverridesHttpStatus() {
  }

  @Override @Ignore("TODO: make a spark.ExceptionMapper that adds the \"error\" request property")
  public void spanHandlerSeesException() {
  }
}

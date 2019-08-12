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
package brave.jersey.server;

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITSpanCustomizingApplicationEventListener extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO!");
  }

  @Test public void tagsResource() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.tags())
      .containsEntry("jaxrs.resource.class", "TestResource")
      .containsEntry("jaxrs.resource.method", "foo");
  }

  /** Tests that the span propagates between under asynchronous callbacks managed by jersey. */
  @Ignore("TODO: investigate race condition")
  @Test public void managedAsync() throws Exception {
    get("/managedAsync");

    takeSpan();
  }

  @Override public void init(ServletContextHandler handler) {
    ResourceConfig config = new ResourceConfig();
    config.register(new TestResource(httpTracing));
    config.register(SpanCustomizingApplicationEventListener.create());
    handler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");

    // add the underlying servlet tracing filter which the event listener decorates with more tags
    Dynamic filterRegistration =
      handler.getServletContext().addFilter("tracingFilter", TracingFilter.create(httpTracing));
    filterRegistration.setAsyncSupported(true);
    // isMatchAfter=true is required for async tests to pass!
    filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}

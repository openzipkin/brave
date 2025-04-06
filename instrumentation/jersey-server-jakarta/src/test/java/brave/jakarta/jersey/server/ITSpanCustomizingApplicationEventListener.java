/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.Span;
import brave.jakarta.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import brave.test.jakarta.http.Jetty11ServerController;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import java.util.EnumSet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ITSpanCustomizingApplicationEventListener extends ITServletContainer {

  public ITSpanCustomizingApplicationEventListener() {
    super(new Jetty11ServerController(), Log.getLogger("org.eclipse.jetty.util.log"));
  }

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO!");
  }

  @Test void tagsResource() throws Exception {
    get("/foo");

    assertThat(testSpanHandler.takeRemoteSpan(Span.Kind.SERVER).tags())
      .containsEntry("jaxrs.resource.class", "TestResource")
      .containsEntry("jaxrs.resource.method", "foo");
  }

  /** Tests that the span propagates between under asynchronous callbacks managed by jersey. */
  @Disabled("TODO: investigate race condition")
  @Test void managedAsync() throws Exception {
    get("/managedAsync");

    testSpanHandler.takeRemoteSpan(Span.Kind.SERVER);
  }

  @Override public void init(ServletContextHandler handler) {
    ResourceConfig config = new ResourceConfig();
    config.register(new TestResource(httpTracing));
    config.register(SpanCustomizingApplicationEventListener.create());
    handler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");

    // add the underlying servlet tracing filter which the event listener decorates with more tags
    FilterRegistration.Dynamic filterRegistration =
      handler.getServletContext().addFilter("tracingFilter", TracingFilter.create(httpTracing));
    filterRegistration.setAsyncSupported(true);
    // isMatchAfter=true is required for async tests to pass!
    filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}

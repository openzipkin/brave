/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jersey.server;

import brave.Span;
import brave.test.http.ITServletContainer;
import brave.test.http.Jetty9ServerController;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ITTracingApplicationEventListener extends ITServletContainer {
  public ITTracingApplicationEventListener() {
    super(new Jetty9ServerController());
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
  @Test void managedAsync() throws Exception {
    Response response = get("/managedAsync");
    assertThat(response.isSuccessful()).withFailMessage("not successful: " + response).isTrue();

    testSpanHandler.takeRemoteSpan(Span.Kind.SERVER);
  }

  @Override public void init(ServletContextHandler handler) {
    ResourceConfig config = new ResourceConfig();
    config.register(new TestResource(httpTracing));
    config.register(TracingApplicationEventListener.create(httpTracing));
    ServletHolder servlet = new ServletHolder(new ServletContainer(config));
    servlet.setAsyncSupported(true);
    handler.addServlet(servlet, "/*");
  }
}

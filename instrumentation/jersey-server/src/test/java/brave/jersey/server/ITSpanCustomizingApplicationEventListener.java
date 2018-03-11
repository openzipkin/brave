package brave.jersey.server;

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
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
  @Test public void managedAsync() throws Exception {
    get("/managedAsync");

    takeSpan();
  }

  @Override public void init(ServletContextHandler handler) {
    ResourceConfig config = new ResourceConfig();
    config.register(new TestResource(httpTracing));
    config.register(SpanCustomizingApplicationEventListener.create());
    handler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");

    // isMatchAfter=true is required for async tests to pass!
    handler.getServletContext()
        .addFilter("tracingFilter", TracingFilter.create(httpTracing))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}

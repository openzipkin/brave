package brave.jersey.server;

import brave.test.http.ITServletContainer;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingApplicationEventListener extends ITServletContainer {

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
    Response response = get("/managedAsync");
    assertThat(response.isSuccessful()).withFailMessage("not successful: " + response).isTrue();

    takeSpan();
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

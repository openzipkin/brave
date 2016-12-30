package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.internal.Util;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.WebAppDescriptor;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.junit.Test;
import zipkin.TraceKeys;
import zipkin.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This tests injected {@link ServletTraceFilter} and {@link JerseyClientTraceFilter}.
 *
 * <p>This uses Guice as Jersey 1.x doesn't really have an injection system.
 */
public class ITBraveJersey extends JerseyTest {
  public static class GuiceServletConfig extends GuiceServletContextListener {

    @Override
    protected Injector getInjector() {
      return Guice.createInjector(new ServletModule() {

        @Override
        protected void configureServlets() {
          bind(TestResource.class);
          serve("/*").with(GuiceContainer.class);
          filter("/*").through(TraceFilters.INSTANCE.server);
        }
      });
    }
  }

  @Path("test")
  public static class TestResource {

    @GET
    public Response get() {
      return Response.status(200).build();
    }
  }

  public ITBraveJersey() throws Exception {
    super(new WebAppDescriptor.Builder()
        .contextListenerClass(GuiceServletConfig.class)
        .filterClass(GuiceFilter.class)
        .build());
  }

  @Test
  public void test() throws Exception {
    // install the trace filter
    WebResource webResource = resource();
    webResource.addFilter(TraceFilters.INSTANCE.client);

    // hit the server
    webResource.path("test").get(String.class);

    List<List<zipkin.Span>> traces = TraceFilters.INSTANCE.storage.spanStore()
        .getTraces(QueryRequest.builder().build());
    assertThat(traces).hasSize(1);
    assertThat(traces.get(0))
        .withFailMessage("Expected client and server to share ids: " + traces.get(0))
        .hasSize(1);

    zipkin.Span clientServerSpan = traces.get(0).get(0);
    assertThat(clientServerSpan.annotations).extracting(a -> a.value)
        .containsExactly("cs", "sr", "ss", "cr");

    // Currently one side logs the full url where the other logs only the path
    assertThat(clientServerSpan.binaryAnnotations)
        .filteredOn(ba -> ba.key.equals(TraceKeys.HTTP_URL))
        .extracting(ba -> new String(ba.value, Util.UTF_8))
        .allSatisfy(url -> assertThat(url.endsWith("/test")));
  }
}

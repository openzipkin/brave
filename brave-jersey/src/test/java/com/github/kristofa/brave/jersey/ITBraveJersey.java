package com.github.kristofa.brave.jersey;

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
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

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

    List<Span> spansReported = TraceFilters.INSTANCE.spansReported;
    assertEquals(2, spansReported.size());

    // server side finished before client side
    Span serverSpan = spansReported.get(0);
    Span clientSpan = spansReported.get(1);

    // An RPC span shares exactly the same IDs across service boundaries
    assertThat(clientSpan.idString())
        .isEqualTo(serverSpan.idString());

    // Both sides currently log the span name, and since both are HTTP, they have the same default
    assertThat(clientSpan.name)
        .isEqualTo(serverSpan.name)
        .isEqualTo("get");

    assertThat(clientSpan.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");

    assertThat(serverSpan.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr", "ss");

    // both came from the same brave instance, so the local endpoints should be the same.
    assertThat(clientSpan.annotations.get(0).endpoint)
        .isEqualTo(serverSpan.annotations.get(0).endpoint);
  }
}

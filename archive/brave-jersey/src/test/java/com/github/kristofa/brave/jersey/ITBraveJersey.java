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
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.junit.Test;
import zipkin.TraceKeys;
import zipkin2.Span;

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

    List<List<zipkin2.Span>> traces = TraceFilters.INSTANCE.storage.spanStore().getTraces();
    assertThat(traces).hasSize(1);
    List<Span> spans = traces.get(0);

    assertThat(spans.get(0).id())
        .withFailMessage("Expected client and server to share ids: " + spans)
        .isEqualTo(spans.get(1).id());

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(Span.Kind.SERVER, Span.Kind.CLIENT);

    // Currently one side logs the full url where the other logs only the path
    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .filteredOn(e -> e.getKey().equals(TraceKeys.HTTP_URL))
        .extracting(Map.Entry::getValue)
        .allSatisfy(url -> assertThat(url.endsWith("/test")));
  }
}

package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ITBraveJersey2 extends JerseyTest {
  static List<zipkin.Span> spansReported = new ArrayList<>();
  static Brave brave = new Brave.Builder("brave-jersey2").reporter(spansReported::add).build();

  @Path("test")
  public static class TestResource {

    @GET
    public Response get() {
      return Response.status(200).build();
    }
  }

  @Override
  protected Application configure() {
    return new ResourceConfig()
        .register(BraveTracingFeature.create(brave))
        .register(TestResource.class);
  }

  @Test
  public void test() throws Exception {
    // install the trace filter
    WebTarget target = target("test");
    target.register(BraveTracingFeature.create(brave));

    // hit the server
    final Response response = target.request().get();
    assertEquals(200, response.getStatus());

    assertEquals(2, spansReported.size());

    // server side finished before client side
    zipkin.Span serverSpan = spansReported.get(0);
    zipkin.Span clientSpan = spansReported.get(1);

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

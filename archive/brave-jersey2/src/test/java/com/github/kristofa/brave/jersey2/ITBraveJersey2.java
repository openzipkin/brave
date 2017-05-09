package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.internal.Util;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;
import zipkin.TraceKeys;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class ITBraveJersey2 extends JerseyTest {
  static InMemoryStorage storage = new InMemoryStorage();
  static Brave brave = new Brave.Builder("brave-jersey")
      .reporter(s -> storage.spanConsumer().accept(Collections.singletonList(s))).build();

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
    assertThat(response.getStatus()).isEqualTo(200);

    List<List<zipkin.Span>> traces = storage.spanStore()
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
        .withFailMessage("Expected http urls to be same")
        .hasSize(1);
  }
}

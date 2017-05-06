package brave.jaxrs2;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingFeature_Client extends ITHttpClient<Client> {

  ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override protected Client newClient(int port) {
    return new ResteasyClientBuilder()
        .asyncExecutor(httpTracing.tracing().currentTraceContext().executorService(executor))
        .socketTimeout(1, TimeUnit.SECONDS)
        .register(TracingFeature.create(httpTracing))
        .build();
  }

  @Override protected void closeClient(Client client) throws IOException {
    client.close();
    executor.shutdown();
  }

  @Override protected void get(Client client, String pathIncludingQuery) throws IOException {
    client.target(url(pathIncludingQuery)).request().buildGet().invoke().close();
  }

  @Override protected void post(Client client, String pathIncludingQuery, String body)
      throws Exception {
    client.target(url(pathIncludingQuery)).request()
        .buildPost(Entity.text(body))
        .invoke().close();
  }

  @Override protected void getAsync(Client client, String pathIncludingQuery) throws Exception {
    client.target(url(pathIncludingQuery)).request().async().get();
  }

  @Test public void currentSpanVisibleToUserFilters() throws Exception {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = new ResteasyClientBuilder()
        .asyncExecutor(httpTracing.tracing().currentTraceContext().executorService(executor))
        .register(TracingFeature.create(httpTracing))
        .register((ClientRequestFilter) requestContext ->
            requestContext.getHeaders()
                .putSingle("my-id", currentTraceContext.get().traceIdString())
        ).build();

    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }

  @Override @Test(expected = AssertionError.class)
  public void redirect() throws Exception { // blind to the implementation of redirects
    super.redirect();
  }

  @Override @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void reportsSpanOnTransportException() throws Exception {
    super.reportsSpanOnTransportException();
  }

  @Override @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }
}

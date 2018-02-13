package brave.jaxrs2;

import brave.http.ITHttpAsyncClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingFeature_Client extends ITHttpAsyncClient<Client> {

  ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override protected Client newClient(int port) {
    return new ResteasyClientBuilder()
        .asyncExecutor(httpTracing.tracing().currentTraceContext().executorService(executor))
        .socketTimeout(1, TimeUnit.SECONDS)
        .register(TracingFeature.create(httpTracing))
        .build();
  }

  @Override protected void closeClient(Client client) {
    client.close();
    executor.shutdown();
  }

  @Override protected void get(Client client, String pathIncludingQuery) {
    client.target(url(pathIncludingQuery)).request().buildGet().invoke().close();
  }

  @Override protected void post(Client client, String pathIncludingQuery, String body) {
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

    takeSpan();
  }

  @Override @Ignore("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Ignore("doesn't know the remote address")
  public void reportsServerAddress() {
  }

  @Override protected Span checkReportsSpanOnTransportException() throws InterruptedException {
    throw new AssumptionViolatedException("doesn't yet close a span on exception");
  }
}

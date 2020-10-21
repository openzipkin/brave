package brave.httpclient5;

import brave.Span;
import brave.test.http.ITHttpClient;
import java.io.IOException;
import java.net.URI;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.Test;

import static org.apache.hc.core5.http.io.entity.EntityUtils.consume;
import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingHttpClientBuilder extends ITHttpClient<CloseableHttpClient> {
  @Override
  protected CloseableHttpClient newClient(int port) {
    return TracingHttpClients.create(httpTracing,
      HttpClientBuilder.create().disableAutomaticRetries());
  }

  @Override
  protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override
  protected void options(CloseableHttpClient client, String path) throws IOException {
    consume(client.execute(new HttpOptions(URI.create(url(path)))).getEntity());
  }

  @Override
  protected void get(CloseableHttpClient client, String pathIncludingQuery) throws IOException {
    consume(client.execute(new HttpGet(URI.create(url(pathIncludingQuery)))).getEntity());
  }

  @Override
  protected void post(CloseableHttpClient client, String pathIncludingQuery, String body)
    throws IOException {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new StringEntity(body));
    consume(client.execute(post).getEntity());
  }

  @Test
  public void currentSpanVisibleToUserFilters() throws IOException {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingHttpClients.create(
      httpTracing,
      HttpClientBuilder.create()
        .disableAutomaticRetries()
        .addRequestInterceptorFirst(
          (request, entity, context) ->
            request.setHeader("my-id", currentTraceContext.get().traceIdString())
        ));

    get(client, "/foo");

    RecordedRequest request = takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    testSpanHandler.takeRemoteSpan(Span.Kind.CLIENT);
  }
}

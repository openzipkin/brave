package brave.httpclient;

import okhttp3.mockwebserver.MockResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingCachingHttpClientBuilder extends ITTracingHttpClientBuilder {
  @Override protected CloseableHttpClient newClient(int port) {
    return TracingCachingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }

  /**
   * Handle when the client doesn't actually make a client span
   *
   * <p>See https://github.com/openzipkin/brave/issues/864
   */
  @Test public void cacheControl() throws Exception {
    server.enqueue(new MockResponse()
        .addHeader("Content-Type", "text/plain")
        .addHeader("Cache-Control", "max-age=600, stale-while-revalidate=1200")
        .setBody("Hello"));

    // important to use a different path than other tests!
    get(client, "/cached");
    get(client, "/cached");

    assertThat(server.getRequestCount()).isEqualTo(1);

    Span first = takeSpan();
    assertThat(first.kind()).isEqualTo(Span.Kind.CLIENT);
    Span second = takeSpan();
    assertThat(second.kind()).isNull();
    assertThat(second.tags()).containsKey("http.cache_hit");
  }
}

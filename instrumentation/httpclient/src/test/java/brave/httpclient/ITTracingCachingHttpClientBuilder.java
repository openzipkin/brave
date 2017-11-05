package brave.httpclient;

import org.apache.http.impl.client.CloseableHttpClient;

public class ITTracingCachingHttpClientBuilder extends ITTracingHttpClientBuilder {
  @Override protected CloseableHttpClient newClient(int port) {
    return TracingCachingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }
}

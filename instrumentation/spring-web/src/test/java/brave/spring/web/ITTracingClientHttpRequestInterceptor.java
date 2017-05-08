package brave.spring.web;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingClientHttpRequestInterceptor
    extends ITHttpClient<OkHttp3ClientHttpRequestFactory> {

  ClientHttpRequestInterceptor interceptor;

  OkHttp3ClientHttpRequestFactory configureClient(
      ClientHttpRequestInterceptor interceptor) {
    OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory();
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected OkHttp3ClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(OkHttp3ClientHttpRequestFactory client) throws IOException {
    client.destroy();
  }

  @Override protected void get(OkHttp3ClientHttpRequestFactory client, String pathIncludingQuery)
      throws Exception {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForObject(url(pathIncludingQuery), String.class);
  }

  @Override
  protected void post(OkHttp3ClientHttpRequestFactory client, String pathIncludingQuery,
      String content) throws Exception {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForObject(url(pathIncludingQuery), content, String.class);
  }

  @Override
  protected void getAsync(OkHttp3ClientHttpRequestFactory client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("TODO: async rest template has its own interceptor");
  }

  @Test
  public void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());

    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Arrays.asList(interceptor, (request, body, execution) -> {
      request.getHeaders()
          .add("my-id", currentTraceContext.get().traceIdString());
      return execution.execute(request, body);
    }));
    restTemplate.getForObject(server.url("/foo").toString(), String.class);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }

  @Override @Test(expected = AssertionError.class)
  public void redirect() throws Exception { // blind to the implementation of redirects
    super.redirect();
  }

  @Override @Test(expected = AssertionError.class)
  public void reportsServerAddress() throws Exception { // doesn't know the remote address
    super.reportsServerAddress();
  }
}

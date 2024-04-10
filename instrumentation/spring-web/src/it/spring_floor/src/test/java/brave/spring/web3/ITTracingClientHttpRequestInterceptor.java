/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.web3;

import brave.Span;
import brave.spring.web.TracingClientHttpRequestInterceptor;
import brave.test.http.ITHttpClient;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ITTracingClientHttpRequestInterceptor extends ITHttpClient<ClientHttpRequestFactory> {
  ClientHttpRequestInterceptor interceptor;

  ClientHttpRequestFactory configureClient(ClientHttpRequestInterceptor interceptor) {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected ClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(ClientHttpRequestFactory client) {
    // unnecessary to cleanup as the IT runs in a sub-process
  }

  @Override protected void get(ClientHttpRequestFactory client, String pathIncludingQuery) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForObject(url(pathIncludingQuery), String.class);
  }

  @Override protected void options(ClientHttpRequestFactory client, String path) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.optionsForAllow(url(path), String.class);
  }

  @Override protected void post(ClientHttpRequestFactory client, String uri, String content) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForObject(url(uri), content, String.class);
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws InterruptedException {
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

    testSpanHandler.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Override @Disabled("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Disabled("doesn't know the remote address")
  public void reportsServerAddress() {
  }
}

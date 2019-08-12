/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.spring.web;

import brave.test.http.ITHttpClient;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingClientHttpRequestInterceptor extends ITHttpClient<ClientHttpRequestFactory> {
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

  @Override protected void closeClient(ClientHttpRequestFactory client) throws Exception {
    ((HttpComponentsClientHttpRequestFactory) client).destroy();
  }

  @Override protected void get(ClientHttpRequestFactory client, String pathIncludingQuery) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForObject(url(pathIncludingQuery), String.class);
  }

  @Override protected void post(ClientHttpRequestFactory client, String uri, String content) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForObject(url(uri), content, String.class);
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
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

    takeSpan();
  }

  @Override @Ignore("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Ignore("doesn't know the remote address")
  public void reportsServerAddress() {
  }
}

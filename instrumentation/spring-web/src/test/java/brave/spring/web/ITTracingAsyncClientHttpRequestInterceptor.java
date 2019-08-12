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

import brave.test.http.ITHttpAsyncClient;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.AsyncRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingAsyncClientHttpRequestInterceptor
  extends ITHttpAsyncClient<AsyncClientHttpRequestFactory> {
  AsyncClientHttpRequestInterceptor interceptor;

  AsyncClientHttpRequestFactory configureClient(AsyncClientHttpRequestInterceptor interceptor) {
    HttpComponentsAsyncClientHttpRequestFactory factory =
      new HttpComponentsAsyncClientHttpRequestFactory();
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected AsyncClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingAsyncClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(AsyncClientHttpRequestFactory client) throws Exception {
    ((HttpComponentsClientHttpRequestFactory) client).destroy();
  }

  @Override protected void get(AsyncClientHttpRequestFactory client, String pathIncludingQuery)
    throws Exception {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(pathIncludingQuery), String.class).get();
  }

  @Override protected void post(AsyncClientHttpRequestFactory client, String uri, String content)
    throws Exception {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForEntity(url(uri), RequestEntity.post(URI.create(url(uri))).body(content),
      String.class).get();
  }

  @Override protected void getAsync(AsyncClientHttpRequestFactory client, String uri) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(uri), String.class);
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());

    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Arrays.asList(interceptor, (request, body, execution) -> {
      request.getHeaders()
        .add("my-id", currentTraceContext.get().traceIdString());
      return execution.executeAsync(request, body);
    }));
    restTemplate.getForEntity(server.url("/foo").toString(), String.class).get();

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

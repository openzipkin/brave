/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import brave.test.util.AssertableCallback;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingAsyncClientHttpRequestInterceptor
  extends ITHttpAsyncClient<AsyncClientHttpRequestFactory> {
  AsyncClientHttpRequestInterceptor interceptor;
  CloseableHttpAsyncClient asyncClient = HttpAsyncClients.createSystem();

  @After @Override public void close() throws Exception {
    asyncClient.close();
    super.close();
  }

  AsyncClientHttpRequestFactory configureClient(AsyncClientHttpRequestInterceptor interceptor) {
    HttpComponentsAsyncClientHttpRequestFactory factory =
      new HttpComponentsAsyncClientHttpRequestFactory(asyncClient);
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected AsyncClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingAsyncClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(AsyncClientHttpRequestFactory client) {
    // done in close()
  }

  @Override protected void get(AsyncClientHttpRequestFactory client, String pathIncludingQuery) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(pathIncludingQuery), String.class).completable().join();
  }

  @Override protected void post(AsyncClientHttpRequestFactory client, String uri, String content) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForEntity(url(uri), RequestEntity.post(URI.create(url(uri))).body(content),
      String.class).completable().join();
  }

  @Override protected void getAsync(AsyncClientHttpRequestFactory client, String path,
    AssertableCallback<Integer> callback) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(path), String.class)
      .addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
        @Override public void onFailure(Throwable throwable) {
          if (throwable instanceof HttpStatusCodeException) {
            callback.onSuccess(((HttpStatusCodeException) throwable).getRawStatusCode());
          } else {
            callback.onError(throwable);
          }
        }

        @Override public void onSuccess(ResponseEntity<String> entity) {
          callback.onSuccess(entity.getStatusCodeValue());
        }
      });
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

    RecordedRequest request = takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Override @Ignore("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Ignore("doesn't know the remote address")
  public void reportsServerAddress() {
  }

  @Override @Ignore("sometimes the client span last longer than the future")
  // ignoring flakes as AsyncRestTemplate is deprecated anyway and only impact is inaccurate timing
  public void clientTimestampAndDurationEnclosedByParent() {
  }
}

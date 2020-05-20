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
package brave.httpasyncclient;

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import static brave.Span.Kind.CLIENT;
import static org.apache.commons.codec.Charsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ITTracingHttpAsyncClientBuilder extends ITHttpAsyncClient<CloseableHttpAsyncClient> {
  @Override protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result = TracingHttpAsyncClientBuilder.create(httpTracing).build();
    result.start();
    return result;
  }

  @Override protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
    throws IOException {
    HttpGet get = new HttpGet(URI.create(url(pathIncludingQuery)));
    invoke(client, get);
  }

  @Override
  protected void post(CloseableHttpAsyncClient client, String pathIncludingQuery, String body)
    throws IOException {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new NStringEntity(body, UTF_8));
    invoke(client, post);
  }

  static void invoke(CloseableHttpAsyncClient client, HttpUriRequest req) throws IOException {
    Future<HttpResponse> future = client.execute(req, null);
    HttpResponse response = blockOnFuture(future);
    EntityUtils.consume(response.getEntity());
  }

  @Test public void currentSpanVisibleToUserFilters() throws IOException {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingHttpAsyncClientBuilder.create(httpTracing)
      .addInterceptorLast((HttpRequestInterceptor) (request, context) ->
        request.setHeader("my-id", currentTraceContext.get().traceIdString())
      ).build();
    client.start();

    get(client, "/foo");

    RecordedRequest request = takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test public void failedInterceptorRemovesScope() {
    assertThat(currentTraceContext.get()).isNull();
    RuntimeException error = new RuntimeException("Test");
    client = TracingHttpAsyncClientBuilder.create(httpTracing)
      .addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
        throw error;
      }).build();
    client.start();

    assertThatThrownBy(() -> get(client, "/foo"))
      .isSameAs(error);

    assertThat(currentTraceContext.get()).isNull();

    testSpanHandler.takeRemoteSpanWithError(CLIENT, error);
  }

  @Override
  protected void get(CloseableHttpAsyncClient client, String path,
    BiConsumer<Integer, Throwable> callback) {
    HttpGet get = new HttpGet(URI.create(url(path)));
    client.execute(get, new FutureCallback<HttpResponse>() {
      @Override public void completed(HttpResponse res) {
        callback.accept(res.getStatusLine().getStatusCode(), null);
      }

      @Override public void failed(Exception ex) {
        callback.accept(null, ex);
      }

      @Override public void cancelled() {
        callback.accept(null, new CancellationException());
      }
    });
  }

  /** Ensures we don't wrap exception messages. */
  static <V> V blockOnFuture(Future<V> future) throws IOException {
    try {
      return future.get(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      Throwable er = e.getCause();
      if (er instanceof RuntimeException) throw (RuntimeException) er;
      if (er instanceof IOException) throw (IOException) er;
      throw new AssertionError(e);
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }
}

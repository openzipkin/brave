/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import brave.test.http.ITHttpAsyncClient;
import brave.test.util.AssertableCallback;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ITTracingHttpAsyncClientBuilder extends ITHttpAsyncClient<CloseableHttpAsyncClient> {
  static void invoke(CloseableHttpAsyncClient client, SimpleHttpRequest req) throws IOException {
    Future<SimpleHttpResponse> future = client.execute(req, null);
    blockOnFuture(future);
  }

  static <V> V blockOnFuture(Future<V> future) throws IOException {
    try {
      return future.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  protected HttpAsyncClientBuilder newClientBuilder() {
    return HttpAsyncClientBuilder.create().disableAutomaticRetries();
  }

  @Override
  protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result =
      HttpClient5Tracing.newBuilder(httpTracing).build(newClientBuilder());
    result.start();
    return result;
  }

  @Override
  protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  @Override
  protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
    throws IOException {
    invoke(client, SimpleRequestBuilder.get(URI.create(url(pathIncludingQuery))).build());
  }

  @Override
  protected void options(CloseableHttpAsyncClient client, String path) throws IOException {
    invoke(client, SimpleRequestBuilder.options(URI.create(url(path))).build());
  }

  @Override
  protected void post(CloseableHttpAsyncClient client, String pathIncludingQuery, String body)
    throws IOException {
    SimpleHttpRequest post = SimpleRequestBuilder.post(URI.create(url(pathIncludingQuery))).build();
    post.setBody(body, ContentType.TEXT_PLAIN);
    invoke(client, post);
  }

  @Override
  protected void get(CloseableHttpAsyncClient client, String path,
    BiConsumer<Integer, Throwable> callback) {
    SimpleHttpRequest get = SimpleRequestBuilder.get(URI.create(url(path))).build();
    client.execute(get, new FutureCallback<SimpleHttpResponse>() {
      @Override
      public void completed(SimpleHttpResponse res) {
        callback.accept(res.getCode(), null);
      }

      @Override
      public void failed(Exception ex) {
        callback.accept(null, ex);
      }

      @Override
      public void cancelled() {
        callback.accept(null, new CancellationException());
      }
    });
  }

  @Test void currentSpanVisibleToUserFilters() throws IOException {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = HttpClient5Tracing.newBuilder(httpTracing)
      .build(newClientBuilder()
        .addRequestInterceptorFirst(
          (httpRequest, entityDetails, httpContext) ->
            httpRequest.setHeader("my-req-id", currentTraceContext.get().traceIdString()))
        .addResponseInterceptorFirst(
          (httpResponse, entityDetails, httpContext) ->
            httpResponse.setHeader("my-res-id", currentTraceContext.get().traceIdString())));
    client.start();

    AssertableCallback<String> callback = new AssertableCallback<>();
    SimpleHttpRequest get = SimpleRequestBuilder.get(URI.create(url("/foo"))).build();
    client.execute(get, new FutureCallback<SimpleHttpResponse>() {
      @Override
      public void completed(SimpleHttpResponse res) {
        callback.accept(res.getFirstHeader("my-res-id").getValue(), null);
      }

      @Override
      public void failed(Exception ex) {
        callback.accept(null, ex);
      }

      @Override
      public void cancelled() {
        callback.accept(null, new CancellationException());
      }
    });

    RecordedRequest request = takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-req-id"));

    String headerString = callback.join();
    assertThat(headerString)
      .isEqualTo(request.getHeader("x-b3-traceId"));

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void failedRequestInterceptorRemovesScope() {
    assertThat(currentTraceContext.get()).isNull();
    RuntimeException error = new RuntimeException("Test");
    client = HttpClient5Tracing.newBuilder(httpTracing)
      .build(newClientBuilder()
        .addRequestInterceptorLast((httpRequest, entityDetails, httpContext) -> {
          throw error;
        }));
    client.start();

    assertThatThrownBy(() -> get(client, "/foo"))
      .hasRootCause(error);

    assertThat(currentTraceContext.get()).isNull();

    testSpanHandler.takeRemoteSpanWithError(CLIENT, error);
  }

  @Test void failedResponseInterceptorRemovesScope() throws IOException {
    server.enqueue(new MockResponse());
    closeClient(client);

    RuntimeException error = new RuntimeException("Test");
    client = HttpClient5Tracing.newBuilder(httpTracing)
      .build(newClientBuilder()
        .addResponseInterceptorLast((httpResponse, entityDetails, httpContext) -> {
          throw error;
        }));

    client.start();

    assertThatThrownBy(() -> get(client, "/foo"))
      .hasRootCause(error);

    assertThat(currentTraceContext.get()).isNull();

    testSpanHandler.takeRemoteSpanWithError(CLIENT, error);
  }
}

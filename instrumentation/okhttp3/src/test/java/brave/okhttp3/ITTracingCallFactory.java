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
package brave.okhttp3;

import brave.ScopedSpan;
import brave.Tracer;
import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingCallFactory extends ITHttpAsyncClient<Call.Factory> {

  @Override protected Call.Factory newClient(int port) {
    return TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
      .connectTimeout(1, TimeUnit.SECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .build()
    );
  }

  @Override protected void closeClient(Call.Factory client) {
    ((TracingCallFactory) client).ok.dispatcher().executorService().shutdownNow();
  }

  @Override protected void get(Call.Factory client, String pathIncludingQuery)
    throws IOException {
    client.newCall(new Request.Builder().url(url(pathIncludingQuery)).build())
      .execute();
  }

  @Override protected void post(Call.Factory client, String pathIncludingQuery, String body)
    throws Exception {
    client.newCall(new Request.Builder().url(url(pathIncludingQuery))
      .post(RequestBody.create(MediaType.parse("text/plain"), body)).build())
      .execute();
  }

  @Override protected void getAsync(Call.Factory client, String pathIncludingQuery) {
    client.newCall(new Request.Builder().url(url(pathIncludingQuery)).build())
      .enqueue(new Callback() {
        @Override public void onFailure(Call call, IOException e) {
          e.printStackTrace();
        }

        @Override public void onResponse(Call call, Response response) {
        }
      });
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
      .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
        .addHeader("my-id", currentTraceContext.get().traceIdString())
        .build()))
      .build());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      get(client, "/foo");
    } finally {
      parent.finish();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    // we report one in-process and one RPC client span
    assertThat(Arrays.asList(takeSpan(), takeSpan()))
      .extracting(Span::kind)
      .containsOnly(null, Span.Kind.CLIENT);
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.okhttp3;

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;

public class ITTracingInterceptor extends ITHttpAsyncClient<Call.Factory> { // public for src/it
  ExecutorService executorService = new Dispatcher().executorService();
  Dispatcher dispatcher = new Dispatcher(currentTraceContext.executorService(executorService));

  @AfterEach @Override public void close() throws Exception {
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    super.close();
  }

  @Override protected Call.Factory newClient(int port) {
    return new OkHttpClient.Builder()
      .connectTimeout(1, TimeUnit.SECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .dispatcher(dispatcher)
      .addNetworkInterceptor(TracingInterceptor.create(httpTracing))
      .build();
  }

  @Override protected void closeClient(Call.Factory client) {
    // done in close()
  }

  @Override protected void get(Call.Factory client, String pathIncludingQuery)
    throws IOException {
    client.newCall(new Request.Builder().url(url(pathIncludingQuery)).build())
      .execute();
  }

  @Override protected void options(Call.Factory client, String path) throws IOException {
    client.newCall(new Request.Builder().method("OPTIONS", null).url(url(path)).build())
      .execute();
  }

  @Override protected void post(Call.Factory client, String pathIncludingQuery, String body)
    throws IOException {
    client.newCall(new Request.Builder().url(url(pathIncludingQuery))
      // intentionally deprecated method so that the v3.x tests can compile
      .post(RequestBody.create(MediaType.parse("text/plain"), body)).build())
      .execute();
  }

  @Override
  protected void get(Call.Factory client, String path, BiConsumer<Integer, Throwable> callback) {
    client.newCall(new Request.Builder().url(url(path)).build())
      .enqueue(new Callback() {
        @Override public void onFailure(Call call, IOException e) {
          callback.accept(null, e);
        }

        @Override public void onResponse(Call call, Response response) {
          callback.accept(response.code(), null);
        }
      });
  }
}

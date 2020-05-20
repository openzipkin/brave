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
import org.junit.After;

public class ITTracingInterceptor extends ITHttpAsyncClient<Call.Factory> {
  ExecutorService executorService = new Dispatcher().executorService();
  Dispatcher dispatcher = new Dispatcher(currentTraceContext.executorService(executorService));

  @After @Override public void close() throws Exception {
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

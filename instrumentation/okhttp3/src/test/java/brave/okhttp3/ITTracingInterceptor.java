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

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ITTracingInterceptor extends ITHttpAsyncClient<Call.Factory> {

  @Override protected Call.Factory newClient(int port) {
    return new OkHttpClient.Builder()
      .connectTimeout(1, TimeUnit.SECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .retryOnConnectionFailure(false)
      .dispatcher(new Dispatcher(
        httpTracing.tracing().currentTraceContext()
          .executorService(new Dispatcher().executorService())
      ))
      .addNetworkInterceptor(TracingInterceptor.create(httpTracing))
      .build();
  }

  @Override protected void closeClient(Call.Factory client) throws IOException {
    ((OkHttpClient) client).dispatcher().executorService().shutdownNow();
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

        @Override public void onResponse(Call call, Response response) throws IOException {
        }
      });
  }
}

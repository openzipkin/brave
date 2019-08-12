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

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class OkHttpClientBenchmarks extends HttpClientBenchmarks<Call.Factory> {

  @Override protected Call.Factory newClient(HttpTracing httpTracing) {
    return TracingCallFactory.create(httpTracing, new OkHttpClient());
  }

  @Override protected Call.Factory newClient() {
    return new OkHttpClient();
  }

  @Override protected void get(Call.Factory client) throws Exception {
    client.newCall(new Request.Builder().url(baseUrl()).build()).execute().body().close();
  }

  @Override protected void close(Call.Factory client) throws IOException {
    OkHttpClient ok;
    if (client instanceof OkHttpClient) {
      ok = (OkHttpClient) client;
    } else {
      ok = ((TracingCallFactory) client).ok;
    }
    ok.dispatcher().executorService().shutdown();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + OkHttpClientBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

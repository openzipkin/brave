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
package brave.httpasyncclient;

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.io.IOException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class ApacheHttpAsyncClientBenchmarks extends
  HttpClientBenchmarks<CloseableHttpAsyncClient> {

  @Override protected CloseableHttpAsyncClient newClient(HttpTracing httpTracing) {
    CloseableHttpAsyncClient result = TracingHttpAsyncClientBuilder.create(httpTracing).build();
    result.start();
    return result;
  }

  @Override protected CloseableHttpAsyncClient newClient() {
    CloseableHttpAsyncClient result = HttpAsyncClients.custom().build();
    result.start();
    return result;
  }

  @Override protected void get(CloseableHttpAsyncClient client) throws Exception {
    EntityUtils.consume(client.execute(new HttpGet(baseUrl()), null).get().getEntity());
  }

  @Override protected void close(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + ApacheHttpAsyncClientBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

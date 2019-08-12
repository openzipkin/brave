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
package brave.httpclient;

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.io.IOException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** Disabled as it crashes on socket problems */
public class ApacheHttpClientBenchmarks extends HttpClientBenchmarks<CloseableHttpClient> {

  @Override protected CloseableHttpClient newClient(HttpTracing httpTracing) {
    return TracingHttpClientBuilder.create(httpTracing)
      .disableAutomaticRetries()
      .build();
  }

  @Override protected CloseableHttpClient newClient() {
    return HttpClients.custom()
      .disableAutomaticRetries()
      .build();
  }

  @Override protected void get(CloseableHttpClient client) throws Exception {
    EntityUtils.consume(client.execute(new HttpGet(baseUrl())).getEntity());
  }

  @Override protected void close(CloseableHttpClient client) throws IOException {
    client.close();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + ApacheHttpClientBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

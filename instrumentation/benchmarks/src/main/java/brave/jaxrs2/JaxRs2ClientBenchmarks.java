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
package brave.jaxrs2;

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.io.IOException;
import javax.ws.rs.client.Client;
import net.ltgt.resteasy.client.okhttp3.OkHttpClientEngine;
import okhttp3.OkHttpClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class JaxRs2ClientBenchmarks extends HttpClientBenchmarks<Client> {

  OkHttpClient ok = new OkHttpClient();

  @Override protected Client newClient(HttpTracing httpTracing) {
    return new ResteasyClientBuilder()
      .httpEngine(new OkHttpClientEngine(ok))
      .register(TracingClientFilter.create(httpTracing))
      .build();
  }

  @Override protected Client newClient() {
    return new ResteasyClientBuilder()
      .httpEngine(new OkHttpClientEngine(ok))
      .build();
  }

  @Override protected void get(Client client) throws Exception {
    client.target(baseUrl()).request().buildGet().invoke().close();
  }

  @Override protected void close(Client client) throws IOException {
    ok.dispatcher().executorService().shutdown();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + JaxRs2ClientBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

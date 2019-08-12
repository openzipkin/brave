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
package brave.spring.web;

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.io.IOException;
import java.util.Collections;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class RestTemplateBenchmarks extends HttpClientBenchmarks<RestTemplate> {

  OkHttpClient ok = new OkHttpClient();

  @Override protected RestTemplate newClient(HttpTracing httpTracing) {
    OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(ok);
    RestTemplate result = new RestTemplate(factory);
    result.setInterceptors(Collections.singletonList(
      TracingClientHttpRequestInterceptor.create(httpTracing
      )));
    return result;
  }

  @Override protected RestTemplate newClient() {
    return new RestTemplate(new OkHttp3ClientHttpRequestFactory(ok));
  }

  @Override protected void get(RestTemplate client) throws Exception {
    client.getForObject(baseUrl(), String.class);
  }

  @Override protected void close(RestTemplate client) throws IOException {
    ok.dispatcher().executorService().shutdown();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + RestTemplateBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}

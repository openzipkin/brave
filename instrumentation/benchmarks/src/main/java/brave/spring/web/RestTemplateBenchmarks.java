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

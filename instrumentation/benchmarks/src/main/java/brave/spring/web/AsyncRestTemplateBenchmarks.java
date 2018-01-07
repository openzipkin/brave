package brave.spring.web;

import brave.http.HttpClientBenchmarks;
import brave.http.HttpTracing;
import java.util.Collections;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.AsyncRestTemplate;

public class AsyncRestTemplateBenchmarks extends HttpClientBenchmarks<AsyncRestTemplate> {

  OkHttpClient ok = new OkHttpClient();

  @Override protected AsyncRestTemplate newClient(HttpTracing httpTracing) {
    OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(ok);
    AsyncRestTemplate result = new AsyncRestTemplate(factory);
    result.setInterceptors(Collections.singletonList(
        TracingAsyncClientHttpRequestInterceptor.create(httpTracing
        )));
    return result;
  }

  @Override protected AsyncRestTemplate newClient() {
    return new AsyncRestTemplate(new OkHttp3ClientHttpRequestFactory(ok));
  }

  @Override protected void get(AsyncRestTemplate client) throws Exception {
    client.getForEntity(baseUrl(), String.class).get();
  }

  @Override protected void close(AsyncRestTemplate client) {
    ok.dispatcher().executorService().shutdownNow();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + AsyncRestTemplateBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}

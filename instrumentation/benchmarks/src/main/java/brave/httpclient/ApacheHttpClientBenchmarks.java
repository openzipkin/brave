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

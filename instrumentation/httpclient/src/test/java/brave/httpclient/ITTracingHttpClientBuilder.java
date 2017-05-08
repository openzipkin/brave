package brave.httpclient;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.AssumptionViolatedException;

public class ITTracingHttpClientBuilder extends ITHttpClient<CloseableHttpClient> {

  @Override protected CloseableHttpClient newClient(int port) {
    return TracingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }

  @Override protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.execute(new HttpGet(URI.create(url(pathIncludingQuery)))).close();
  }

  @Override protected void post(CloseableHttpClient client, String pathIncludingQuery, String body)
      throws Exception {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new StringEntity(body));
    client.execute(post).close();
  }

  @Override protected void getAsync(CloseableHttpClient client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("This is not an async library");
  }
}

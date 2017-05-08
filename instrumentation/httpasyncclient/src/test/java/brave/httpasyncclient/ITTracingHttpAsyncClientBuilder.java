package brave.httpasyncclient;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.net.URI;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;

public class ITTracingHttpAsyncClientBuilder extends ITHttpClient<CloseableHttpAsyncClient> {

  @Override protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result = TracingHttpAsyncClientBuilder.create(httpTracing).build();
    result.start();
    return result;
  }

  @Override protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
      throws Exception {
    HttpGet get = new HttpGet(URI.create(url(pathIncludingQuery)));
    EntityUtils.consume(client.execute(get, null).get().getEntity());
  }

  @Override
  protected void post(CloseableHttpAsyncClient client, String pathIncludingQuery, String body)
      throws Exception {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new NStringEntity(body));
    EntityUtils.consume(client.execute(post, null).get().getEntity());
  }

  @Override protected void getAsync(CloseableHttpAsyncClient client, String pathIncludingQuery) {
    client.execute(new HttpGet(URI.create(url(pathIncludingQuery))), null);
  }
}

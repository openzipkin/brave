package brave.vertx.web.client;

import brave.test.http.ITHttpAsyncClient;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;

// TODO: this doesn't actually use vertx-client-web as we don't have hooks we need
// https://github.com/vert-x3/vertx-web/issues/891
public class ITVertxHttpClientTracing extends ITHttpAsyncClient<HttpClient> {
  Vertx vertx = Vertx.vertx(new VertxOptions());

  @Override protected HttpClient newClient(int port) {
    return vertx.createHttpClient(new HttpClientOptions()
    .setDefaultPort(port)
    .setDefaultHost("127.0.0.1"));
  }

  @Override protected void closeClient(HttpClient client) {
    client.close();
  }

  @Override protected void get(HttpClient client, String pathIncludingQuery) {
    TracingHttpClientRequestHandler handler = new TracingHttpClientRequestHandler(httpTracing);
    HttpClientRequest request = client.get(pathIncludingQuery);
    handler.handle(request);
    request.end();
  }

  @Override
  protected void post(HttpClient client, String pathIncludingQuery, String body) {
    TracingHttpClientRequestHandler handler = new TracingHttpClientRequestHandler(httpTracing);
    HttpClientRequest request = client.post(pathIncludingQuery);
    handler.handle(request);
    request.end(body);
  }

  @Override protected void getAsync(HttpClient client, String pathIncludingQuery) {
    TracingHttpClientRequestHandler handler = new TracingHttpClientRequestHandler(httpTracing);
    HttpClientRequest request = client.get(pathIncludingQuery);
    handler.handle(request);
    request.end();
  }
}

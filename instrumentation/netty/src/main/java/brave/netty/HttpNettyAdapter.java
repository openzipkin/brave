package brave.netty;

import brave.http.HttpServerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

class HttpNettyAdapter extends HttpServerAdapter<HttpRequest, HttpResponse> {
  @Override public String method(HttpRequest request) {
    return request.method().name();
  }

  @Override public String url(HttpRequest request) {
    StringBuffer url = new StringBuffer("http://");
    String host = request.headers().get("HOST");
    if (host != null) url.append(host); // TODO: this will malform on null
    url.append(request.uri());
    return url.toString();
  }

  @Override public String requestHeader(HttpRequest request, String name) {
    return request.headers().get(name);
  }

  @Override public Integer statusCode(HttpResponse response) {
    return response.status().code();
  }
}

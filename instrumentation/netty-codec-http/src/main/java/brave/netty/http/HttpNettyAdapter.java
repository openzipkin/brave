package brave.netty.http;

import brave.http.HttpServerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

final class HttpNettyAdapter extends HttpServerAdapter<HttpRequest, HttpResponse> {
  @Override public String method(HttpRequest request) {
    return request.method().name();
  }

  @Override public String url(HttpRequest request) {
    String host = requestHeader(request, "Host");
    if (host == null) return null;
    // TODO: we don't know if this is really http or https!
    return "http://" + host + request.uri();
  }

  @Override public String requestHeader(HttpRequest request, String name) {
    return request.headers().get(name);
  }

  @Override public Integer statusCode(HttpResponse response) {
    return statusCodeAsInt(response);
  }

  @Override public int statusCodeAsInt(HttpResponse response) {
    return response.status().code();
  }
}

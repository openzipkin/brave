package brave.netty;

import brave.Span;
import brave.http.HttpAdapter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import zipkin.Endpoint;

public class HttpNettyAdapter extends HttpAdapter<HttpRequest, HttpResponse> {
  @Override
  public String method(HttpRequest request) {
    return request.method().name();
  }

  @Override
  public String url(HttpRequest request) {
    StringBuffer url = new StringBuffer("http://");
    String host = request.headers().get("HOST");
    url.append(host);
    url.append(request.uri());
    return url.toString();
  }

  @Override
  public String requestHeader(HttpRequest request, String name) {
    return request.headers().get(name);
  }

  @Override
  public Integer statusCode(HttpResponse response) {
    return response.status().code();
  }

  public static void parseClientAddress(HttpRequest request, Span span) {
    if (span.isNoop()) return;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    HttpHeaders headers = request.headers();
    boolean parsed = builder.parseIp(headers.get("X-Forwarded-For"));
    String host = request.headers().get("HOST");
    String[] strs = host.split(":");
    if (!parsed) parsed = builder.parseIp(strs[0]);
    int port = Integer.valueOf(strs[1]);
    if (parsed) span.remoteEndpoint(builder.port(port).build());
  }
}

package brave.httpclient;

import brave.Span;
import java.net.InetAddress;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;

final class HttpAdapter extends brave.http.HttpClientAdapter<HttpRequestWrapper, HttpResponse> {
  @Override public String method(HttpRequestWrapper request) {
    return request.getRequestLine().getMethod();
  }

  @Override public String path(HttpRequestWrapper request) {
    String result = request.getURI().getPath();
    int queryIndex = result.indexOf('?');
    return queryIndex == -1 ? result : result.substring(0, queryIndex);
  }

  @Override public String url(HttpRequestWrapper request) {
    HttpHost target = request.getTarget();
    if (target != null) return target.toURI() + request.getURI();
    return request.getRequestLine().getUri();
  }

  @Override public String requestHeader(HttpRequestWrapper request, String name) {
    Header result = request.getFirstHeader(name);
    return result != null ? result.getValue() : null;
  }

  @Override public Integer statusCode(HttpResponse response) {
    return statusCodeAsInt(response);
  }

  @Override public int statusCodeAsInt(HttpResponse response) {
    return response.getStatusLine().getStatusCode();
  }

  static void parseTargetAddress(HttpRequestWrapper httpRequest, Span span) {
    if (span.isNoop()) return;
    HttpHost target = httpRequest.getTarget();
    if (target == null) return;
    InetAddress address = target.getAddress();
    if (address != null) {
      if (span.remoteIpAndPort(address.getHostAddress(), target.getPort())) return;
    }
    span.remoteIpAndPort(target.getHostName(), target.getPort());
  }
}

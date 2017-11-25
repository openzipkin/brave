package brave.httpclient;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import zipkin2.Endpoint;

final class HttpAdapter extends brave.http.HttpClientAdapter<HttpRequestWrapper, HttpResponse> {

  @Override
  public boolean parseServerAddress(HttpRequestWrapper httpRequest, Endpoint.Builder builder) {
    HttpHost target = httpRequest.getTarget();
    if (target == null) return false;
    if (builder.parseIp(target.getAddress()) || builder.parseIp(target.getHostName())) {
      int port = target.getPort();
      if (port > 0) builder.port(port);
      return true;
    }
    return false;
  }

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
    return response.getStatusLine().getStatusCode();
  }
}
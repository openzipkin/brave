package brave.servlet;

import brave.Span;
import brave.http.HttpAdapter;
import brave.http.HttpServerHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import zipkin.Endpoint;

/** This can also parse the remote IP of the client. */
// public for others like sparkjava to use
public final class HttpServletAdapter extends HttpAdapter<HttpServletRequest, HttpServletResponse> {
  final ServletRuntime servlet = ServletRuntime.get();

  /**
   * Utility for parsing the remote address, via the "X-Forwarded-For" header, falling back to the
   * {@linkplain HttpServletRequest#getRemoteAddr() remote address}.
   *
   * <p>Typically parsed before {@link HttpServerHandler#handleReceive(Object, Span)} is called.
   */
  public static void parseClientAddress(HttpServletRequest request, Span span) {
    if (span.isNoop()) return;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    boolean parsed = builder.parseIp(request.getHeader("X-Forwarded-For"));
    if (!parsed) parsed = builder.parseIp(request.getRemoteAddr());
    if (parsed) span.remoteEndpoint(builder.port(request.getRemotePort()).build());
  }

  @Override public String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override public String path(HttpServletRequest request) {
    return request.getRequestURI();
  }

  @Override public String url(HttpServletRequest request) {
    StringBuffer url = request.getRequestURL();
    if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
      url.append('?').append(request.getQueryString());
    }
    return url.toString();
  }

  @Override public String requestHeader(HttpServletRequest request, String name) {
    return request.getHeader(name);
  }

  @Override public Integer statusCode(HttpServletResponse response) {
    return servlet.status(response);
  }
}
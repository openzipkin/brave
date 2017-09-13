package brave.servlet;

import brave.http.HttpServerAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import zipkin2.Endpoint;

/** This can also parse the remote IP of the client. */
// public for others like sparkjava to use
public final class HttpServletAdapter
    extends HttpServerAdapter<HttpServletRequest, HttpServletResponse> {
  final ServletRuntime servlet = ServletRuntime.get();

  /**
   * Parses the remote address, via the "X-Forwarded-For" header, falling back to the
   * {@linkplain HttpServletRequest#getRemoteAddr() remote address}.
   */
  @Override public boolean parseClientAddress(HttpServletRequest req, Endpoint.Builder builder) {
    if (builder.parseIp(req.getHeader("X-Forwarded-For")) || builder.parseIp(req.getRemoteAddr())) {
      builder.port(req.getRemotePort());
      return true;
    }
    return false;
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

package brave.servlet;

import brave.http.HttpServerAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import zipkin2.Endpoint;

/** This can also parse the remote IP of the client. */
// public for others like sparkjava to use
public class HttpServletAdapter extends HttpServerAdapter<HttpServletRequest, HttpServletResponse> {

  /**
   * Looks for the {@link HttpServletRequest#setAttribute(String, Object) request attribute}
   * "http.route". When present, returns a response wrapper that this adapter can use to parse it.
   */
  // not static so that this can be overridden by implementations as needed.
  public HttpServletResponse adaptResponse(HttpServletRequest req, HttpServletResponse resp) {
    String httpRoute = (String) req.getAttribute("http.route");
    return httpRoute != null
        ? new DecoratedHttpServletResponse(resp, req.getMethod(), httpRoute)
        : resp;
  }

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

  /**
   * When applied to {@link #adaptResponse(HttpServletRequest, HttpServletResponse)}, returns the
   * {@link HttpServletRequest#getMethod() request method}.
   */
  @Override public String methodFromResponse(HttpServletResponse response) {
    if (response instanceof DecoratedHttpServletResponse) {
      return ((DecoratedHttpServletResponse) response).method;
    }
    return null;
  }

  /**
   * When applied to {@link #adaptResponse(HttpServletRequest, HttpServletResponse)}, returns the
   * {@link HttpServletRequest#getAttribute(String) request attribute} "http.route".
   */
  @Override public String route(HttpServletResponse response) {
    if (response instanceof DecoratedHttpServletResponse) {
      return ((DecoratedHttpServletResponse) response).httpRoute;
    }
    return null;
  }

  @Override public Integer statusCode(HttpServletResponse response) {
    return servlet.status(response);
  }

  static class DecoratedHttpServletResponse extends HttpServletResponseWrapper {
    final String method, httpRoute;

    DecoratedHttpServletResponse(HttpServletResponse response, String method, String httpRoute) {
      super(response);
      this.method = method;
      this.httpRoute = httpRoute;
    }
  }
}

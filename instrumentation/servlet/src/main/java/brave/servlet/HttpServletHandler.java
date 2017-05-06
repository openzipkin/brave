package brave.servlet;

import brave.Span;
import brave.http.HttpAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpServerParser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import zipkin.Endpoint;

/**
 * This is a generic handler which parses the remote IP of the client by default.
 */
public final class HttpServletHandler // public for others like sparkjava to use
    extends HttpServerHandler<HttpServletRequest, HttpServletResponse> {

  public HttpServletHandler(HttpServerParser parser) {
    super(new HttpServletAdapter(), parser);
  }

  @Override public HttpServletRequest handleReceive(HttpServletRequest request, Span span) {
    if (span.isNoop()) return request;
    parseClientAddress(request, span);
    return super.handleReceive(request, span);
  }

  void parseClientAddress(HttpServletRequest request, Span span) {
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    boolean parsed = builder.parseIp(request.getHeader("X-Forwarded-For"));
    if (!parsed) parsed = builder.parseIp(request.getRemoteAddr());
    if (parsed) span.remoteEndpoint(builder.port(request.getRemotePort()).build());
  }

  static final class HttpServletAdapter
      extends HttpAdapter<HttpServletRequest, HttpServletResponse> {
    final ServletRuntime servlet = ServletRuntime.get();

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
}

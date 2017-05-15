package brave.http;

import zipkin.Endpoint;

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {

  /**
   * Returns true if an IP representing the client was readable. Defaults to parse the
   * "X-Forwarded-For" header.
   */
  public boolean parseClientAddress(Req req, Endpoint.Builder builder) {
    String xForwardedFor = requestHeader(req, "X-Forwarded-For");
    return xForwardedFor != null && builder.parseIp(xForwardedFor);
  }
}

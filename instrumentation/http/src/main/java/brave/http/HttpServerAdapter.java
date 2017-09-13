package brave.http;

import zipkin2.Endpoint;

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {

  /**
   * Returns true if an IP representing the client was readable. Defaults to parse the
   * "X-Forwarded-For" header.
   */
  public boolean parseClientAddress(Req req, Endpoint.Builder builder) {
    String xForwardedFor = requestHeader(req, "X-Forwarded-For");
    return xForwardedFor != null && builder.parseIp(xForwardedFor);
  }

  /** @deprecated Please use {@link #parseClientAddress(Object, Endpoint.Builder)} */
  @Deprecated
  public boolean parseClientAddress(Req req, zipkin.Endpoint.Builder builder) {
    return false;
  }
}

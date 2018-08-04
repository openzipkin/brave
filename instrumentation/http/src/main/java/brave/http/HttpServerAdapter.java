package brave.http;

import brave.Span;
import brave.propagation.TraceContext;

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {

  /**
   * @deprecated {@link #parseClientIpAndPort} addresses this functionality. This will be removed in
   * Brave v6.
   */
  @Deprecated public boolean parseClientAddress(Req req, zipkin2.Endpoint.Builder builder) {
    return false;
  }

  /**
   * Used by {@link HttpServerHandler#handleReceive(TraceContext.Extractor, Object, Object)} to add
   * remote socket information about the client. By default, this tries to parse the {@link
   * #parseClientIpFromXForwardedFor(Object, Span) forwarded IP}. Override to add client socket
   * information when forwarded info is not available.
   *
   * <p>Aside: the ability to parse socket information on server request objects is likely even if
   * it is not as likely on the client side. This is because client requests are often parsed before
   * a network route is chosen, whereas server requests are parsed after the network layer.
   *
   * @since 5.2
   */
  public boolean parseClientIpAndPort(Req req, Span span) {
    return parseClientIpFromXForwardedFor(req, span);
  }

  /**
   * Returns the first value in the "X-Forwarded-For" header, or null if not present.
   *
   * @since 5.2
   */
  public boolean parseClientIpFromXForwardedFor(Req req, Span span) {
    String forwardedFor = requestHeader(req, "X-Forwarded-For");
    if (forwardedFor == null) return false;
    int indexOfComma = forwardedFor.indexOf(',');
    if (indexOfComma != -1) forwardedFor = forwardedFor.substring(0, indexOfComma);
    return span.remoteIpAndPort(forwardedFor, 0);
  }
}

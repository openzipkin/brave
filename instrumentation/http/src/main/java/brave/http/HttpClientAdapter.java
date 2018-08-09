package brave.http;

import zipkin2.Endpoint;

public abstract class HttpClientAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {
  /**
   * Returns true if an IP representing the client was readable.
   *
   * @deprecated remote IP information should be added directly by instrumentation. This will be
   * removed in Brave v6.
   */
  @Deprecated  public boolean parseServerIpAndPort(Req req, Endpoint.Builder builder) {
    return false;
  }
}

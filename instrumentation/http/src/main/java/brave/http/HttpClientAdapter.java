package brave.http;

import zipkin2.Endpoint;

public abstract class HttpClientAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {
  /** Returns true if an IP representing the client was readable. */
  public boolean parseServerAddress(Req req, Endpoint.Builder builder) {
    return false;
  }

  /** @deprecated please use {@link #parseServerAddress(Object, zipkin2.Endpoint.Builder)}*/
  @Deprecated
  public boolean parseServerAddress(Req req, zipkin.Endpoint.Builder builder) {
    return false;
  }
}

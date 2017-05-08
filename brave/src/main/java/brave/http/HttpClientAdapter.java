package brave.http;

import zipkin.Endpoint;

public abstract class HttpClientAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {
  /** Returns true if an IP representing the client was readable. */
  public boolean parseServerAddress(Req req, Endpoint.Builder builder) {
    return false;
  }
}

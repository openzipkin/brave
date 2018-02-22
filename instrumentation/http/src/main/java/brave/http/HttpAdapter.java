package brave.http;

import brave.internal.Nullable;
import java.net.URI;

public abstract class HttpAdapter<Req, Resp> {

  /**
   * The HTTP method, or verb, such as "GET" or "POST" or null if unreadable.
   *
   * <p>Conventionally associated with the key "http.method"
   */
  @Nullable public abstract String method(Req request);

  /**
   * The absolute http path, without any query parameters or null if unreadable. Ex.
   * "/objects/abcd-ff"
   *
   * <p>Conventionally associated with the key "http.path"
   */
  @Nullable public String path(Req request) {
    String url = url(request);
    if (url == null) return null;
    return URI.create(url).getPath(); // TODO benchmark
  }

  /**
   * The entire URL, including the scheme, host and query parameters if available or null if
   * unreadable.
   *
   * <p>Conventionally associated with the key "http.url"
   */
  @Nullable public abstract String url(Req request);

  /**
   * Returns one value corresponding to the specified header, or null.
   */
  @Nullable public abstract String requestHeader(Req request, String name);

  /**
   * The HTTP status code or null if unreadable.
   *
   * <p>Conventionally associated with the key "http.status_code"
   *
   * @see #statusCodeAsInt(Object)
   */
  @Nullable public abstract Integer statusCode(Resp response);

  /**
   * Like {@link #statusCode(Object)} except returns a primitive where zero implies absent.
   *
   * <p>Using this method usually avoids allocation, so is encouraged when parsing data.
   */
  public int statusCodeAsInt(Resp response) {
    Integer maybeStatus = statusCode(response);
    return maybeStatus != null ? maybeStatus : 0;
  }

  HttpAdapter() {
  }
}

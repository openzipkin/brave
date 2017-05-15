package brave.http;

import java.net.URI;
import javax.annotation.Nullable;
import zipkin.TraceKeys;

public abstract class HttpAdapter<Req, Resp> {

  /**
   * The HTTP method, or verb, such as "GET" or "POST" or null if unreadable.
   *
   * @see TraceKeys#HTTP_METHOD
   */
  @Nullable public abstract String method(Req request);

  /**
   * The absolute http path, without any query parameters or null if unreadable. Ex.
   * "/objects/abcd-ff"
   *
   * @see TraceKeys#HTTP_PATH
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
   * @see TraceKeys#HTTP_URL
   */
  @Nullable public abstract String url(Req request);

  /**
   * Returns one value corresponding to the specified header, or null.
   */
  @Nullable public abstract String requestHeader(Req request, String name);

  /**
   * The HTTP status code or null if unreadable.
   *
   * @see TraceKeys#HTTP_STATUS_CODE
   */
  @Nullable public abstract Integer statusCode(Resp response);

  HttpAdapter() {
  }
}

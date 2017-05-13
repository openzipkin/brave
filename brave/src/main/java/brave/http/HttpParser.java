package brave.http;

import brave.Tagger;
import brave.internal.Nullable;
import zipkin.Constants;
import zipkin.TraceKeys;

/**
 * Provides reasonable defaults for the data contained in http spans. Subclass to customize,
 * for example, to add tags based on user ID.
 */
public class HttpParser {
  /** Returns the span name of the request. Defaults to the http method. */
  public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req);
  }

  /** By default, this adds the {@link TraceKeys#HTTP_PATH}. */
  public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, Tagger tagger) {
    String path = adapter.path(req);
    if (path != null) tagger.tag(TraceKeys.HTTP_PATH, path);
  }

  /***
   * By default, this adds {@link TraceKeys#HTTP_STATUS_CODE} when it is not 2xx. If there's an
   * exception or the status code is neither 2xx nor 3xx, it adds {@link Constants#ERROR}.
   *
   * <p>Note: Either the response or error parameters may be null, but not both.
   *
   * @see #parseError(Integer, Throwable)
   */
  // This accepts response or exception because sometimes http 500 is an exception and sometimes not
  // If this were not an abstraction, we'd use separate hooks for response and error.
  public <Resp> void responseTags(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, Tagger tagger) {
    Integer httpStatus = res != null ? adapter.statusCode(res) : null;
    if (httpStatus != null && (httpStatus < 200 || httpStatus > 299)) {
      tagger.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
    }
    String message = parseError(httpStatus, error);
    if (message != null) tagger.tag(Constants.ERROR, message);
  }

  /**
   * Returns the {@link TraceKeys#HTTP_STATUS_CODE} when it is not 2xx. If there's an
   * exception or the status code is neither 2xx nor 3xx, it adds {@link Constants#ERROR}.
   *
   * <p>Note: Either the httpStatus or error parameters may be null, but not both
   *
   * @see Constants#ERROR
   */
  @Nullable protected String parseError(@Nullable Integer httpStatus, @Nullable Throwable error) {
    if (error != null) {
      String message = error.getMessage();
      return message != null ? message : error.getClass().getSimpleName();
    }
    if (httpStatus == null) return null;
    return httpStatus < 200 || httpStatus > 399 ? String.valueOf(httpStatus) : null;
  }

  HttpParser() {
  }
}

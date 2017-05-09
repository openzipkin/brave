package brave.http;

import brave.Span;
import brave.internal.Nullable;
import zipkin.TraceKeys;

/**
 * Provides reasonable defaults for the data contained in http server spans. Subclass to customize,
 * for example, to add tags based on user ID.
 */
public class HttpServerParser {
  /** Returns the span name of the request. Defaults to the http method. */
  public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req);
  }

  /**
   * Adds any tags based on the request received from the client.
   *
   * <p>By default, this adds the {@link TraceKeys#HTTP_PATH}.
   */
  public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, Span span) {
    String path = adapter.path(req);
    if (path != null) span.tag(TraceKeys.HTTP_PATH, path);
  }

  /**
   * Adds any tags based on the response sent to the client.
   *
   * <p>By default, this adds the {@link TraceKeys#HTTP_STATUS_CODE} if the status code is >=300.
   */
  public <Resp> void responseTags(HttpAdapter<?, Resp> adapter, Resp res, Span span) {
    Integer httpStatus = adapter.statusCode(res);
    if (httpStatus != null && (httpStatus < 200 || httpStatus > 299)) {
      span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
    }
  }

  /**
   * Returns an {@link zipkin.Constants#ERROR error message}, if there was an error lieu of a
   * response, or if the response sent to the client was an error.
   *
   * <p>By default, this makes an error message based on the exception message, or the status code,
   * if the status code is >=400.
   *
   * <p>Note: Either the response or error parameters may be null, but not both
   */
  @Nullable public <Resp> String error(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error) {
    return adapter.parseError(res, error);
  }
}

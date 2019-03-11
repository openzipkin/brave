package brave.http;

import brave.SpanCustomizer;
import brave.internal.Nullable;

public class HttpParser {
  /**
   * Override to change what data from the http request are parsed into the span representing it. By
   * default, this sets the span name to the http method and tags "http.path"
   *
   * <p>If you only want to change the span name, you can override {@link #spanName(HttpAdapter,
   * Object)} instead.
   *
   * @see #spanName(HttpAdapter, Object)
   */
  public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
    customizer.name(spanName(adapter, req));
    String path = adapter.path(req);
    if (path != null) customizer.tag("http.path", path);
  }

  /** Returns the span name of the request. Defaults to the http method. */
  protected <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req);
  }

  /**
   * Override to change what data from the http response or error are parsed into the span modeling
   * it. By default, this tags "http.status_code" when it is not 2xx. If there's an exception or the
   * status code is below 1xx or above 3xx, it tags "error".
   *
   * <p>If you only want to change how exceptions are parsed, override {@link #error(Integer,
   * Throwable, SpanCustomizer)} instead.
   *
   * <p>Note: Either the response or error parameters may be null, but not both.
   *
   * @see #error(Integer, Throwable, SpanCustomizer)
   */
  // This accepts response or exception because sometimes http 500 is an exception and sometimes not
  // If this were not an abstraction, we'd use separate hooks for response and error.
  public <Resp> void response(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, SpanCustomizer customizer) {
    int statusCode = 0;
    if (res != null) {
      statusCode = adapter.statusCode(res);
      String maybeStatus = maybeStatusAsString(statusCode, 299);
      if (maybeStatus != null) customizer.tag("http.status_code", maybeStatus);
    }
    error(statusCode, error, customizer);
  }

  /** The intent of this is to by default add "http.status_code", when not a success code */
  @Nullable String maybeStatusAsString(int statusCode, int upperRange) {
    if (statusCode != 0 && (statusCode < 200 || statusCode > upperRange)) {
      return String.valueOf(statusCode);
    }
    return null;
  }

  /**
   * Override to change what data from the http error are parsed into the span modeling it. By
   * default, this tags "error" as the exception or the status code if it is below 1xx or above
   * 3xx.
   *
   * <p>Note: Either the httpStatus or error parameters may be null, but not both
   *
   * <p>Conventionally associated with the tag key "error"
   */
  protected void error(@Nullable Integer httpStatus, @Nullable Throwable error,
      SpanCustomizer customizer) {
    String message = null;
    if (error != null) {
      message = error.getMessage();
      if (message == null) message = error.getClass().getSimpleName();
    } else if (httpStatus != null) {
      message = errorFromStatusCode(httpStatus);
    }
    if (message != null) customizer.tag("error", message);
  }

  // Unlike success path tagging, we only want to indicate something as error if it is not in a
  // success range. 1xx-3xx are not errors. It is endpoint-specific if client codes like 404 are
  // in fact errors. That's why this error parsing is overridable.
  @Nullable String errorFromStatusCode(int httpStatus) {
    // Instrumentation error should not make span errors. We don't know the difference between a
    // library being unable to get the http status and a bad status (0). We don't classify zero as
    // error in case instrumentation cannot read the status. This prevents tagging every response as
    // error.
    if (httpStatus == 0) return null;

    // 1xx, 2xx, and 3xx codes are not all valid, but the math is good enough vs drift and opinion
    // about individual codes in the range.
    if (httpStatus < 100 || httpStatus > 399) {
      return String.valueOf(httpStatus);
    }
    return null;
  }

  HttpParser() {
  }
}

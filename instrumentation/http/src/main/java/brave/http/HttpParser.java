package brave.http;

import brave.ErrorParser;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;

public class HttpParser {
  static final ErrorParser DEFAULT_ERROR_PARSER = new ErrorParser();

  /**
   * Override when making custom types. Typically, you'll use {@link Tracing#errorParser()}
   *
   * <pre>{@code
   * class MyHttpClientParser extends HttpClientParser {
   *   ErrorParser errorParser;
   *
   *   MyHttpClientParser(Tracing tracing) {
   *     errorParser = tracing.errorParser();
   *   }
   *
   *   protected ErrorParser errorParser() {
   *     return errorParser;
   *   }
   * --snip--
   * }</pre>
   */
  protected ErrorParser errorParser() {
    return DEFAULT_ERROR_PARSER;
  }

  /**
   * Override to change what data from the http request are parsed into the span representing it. By
   * default, this sets the span name to the http method and tags "http.method" and "http.path".
   *
   * <p>If you only want to change the span name, you can override {@link #spanName(HttpAdapter,
   * Object)} instead.
   *
   * @see #spanName(HttpAdapter, Object)
   */
  // Eventhough the default span name is the method, we have no way of knowing that a user hasn't
  // overwritten the name to something else. If that occurs during response parsing, it is too late
  // to go back and get the http method. Adding http method by default ensures span naming doesn't
  // prevent basic HTTP info from being visible. A cost of this is another tag, but it is small with
  // very limited cardinality. Moreover, users who care strictly about size can override this.
  public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
    customizer.name(spanName(adapter, req));
    String method = adapter.method(req);
    if (method != null) customizer.tag("http.method", method);
    String path = adapter.path(req);
    if (path != null) customizer.tag("http.path", path);
  }

  /** Returns the span name of the request. Defaults to the http method. */
  protected <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req);
  }

  /**
   * Override to change what data from the http response or error are parsed into the span modeling
   * it.
   *
   * <p>By default, this tags "http.status_code" when it is not 2xx. If there's an exception or the
   * status code is neither 2xx nor 3xx, it tags "error". This also overrides the span name based on
   * the {@link HttpAdapter#methodFromResponse(Object)} and {@link HttpAdapter#route(Object)} where
   * possible (ex "get /users/:userId").
   *
   * <p>If routing is supported, and a GET didn't match due to 404, the span name will be
   * "get not_found". If it didn't match due to redirect, the span name will be "get redirected". If
   * routing is not supported, the span name is left alone.
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
      statusCode = adapter.statusCodeAsInt(res);
      String nameFromRoute = spanNameFromRoute(adapter, res, statusCode);
      if (nameFromRoute != null) customizer.name(nameFromRoute);
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

  static <Resp> String spanNameFromRoute(HttpAdapter<?, Resp> adapter, Resp res, int statusCode) {
    String method = adapter.methodFromResponse(res);
    if (method == null) return null; // don't undo a valid name elsewhere
    String route = adapter.route(res);
    if (route == null) return null; // don't undo a valid name elsewhere
    if (!"".equals(route)) return method + " " + route;
    if (statusCode / 100 == 3) return method + " redirected";
    if (statusCode == 404) return method + " not_found";
    return null; // unexpected
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
  // BRAVE6: httpStatus is a Integer, not a int. We can't change this api as users expect this to be
  // called by default. Unfortunately, this implies boxing until we can change it.
  protected void error(@Nullable Integer httpStatus, @Nullable Throwable error,
      SpanCustomizer customizer) {
    if (error != null) {
      errorParser().error(error, customizer);
      return;
    }
    if (httpStatus == null) return;
    // Unlike success path tagging, we only want to indicate something as error if it is not in a
    // success range. 1xx-3xx are not errors. It is endpoint-specific if client codes like 404 are
    // in fact errors. That's why this is overridable.
    int httpStatusInt = httpStatus;

    // Instrumentation error should not make span errors. We don't know the difference between a
    // library being unable to get the http status and a bad status (0). We don't classify zero as
    // error in case instrumentation cannot read the status. This prevents tagging every response as
    // error.
    if (httpStatusInt == 0) return;

    // 1xx, 2xx, and 3xx codes are not all valid, but the math is good enough vs drift and opinion
    // about individual codes in the range.
    if (httpStatusInt < 100 || httpStatusInt > 399) {
      customizer.tag("error", String.valueOf(httpStatusInt));
    }
  }
}

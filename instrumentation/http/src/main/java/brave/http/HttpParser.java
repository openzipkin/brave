package brave.http;

import brave.SpanCustomizer;
import brave.internal.Nullable;

public class HttpParser {
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
   * the {@link HttpAdapter#route(Object)} where possible.
   *
   * <p>If routing is supported, but didn't match due to 404, the span name will be "not_found". If
   * it didn't match due to redirect, the span name will be "redirected". If routing is not
   * supported, the span name is left alone.
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
      String nameFromRoute = spanNameFromRoute(adapter.route(res), statusCode);
      if (nameFromRoute != null) customizer.name(nameFromRoute);
      if (statusCode != 0 && (statusCode < 200 || statusCode > 299)) {
        customizer.tag("http.status_code", String.valueOf(statusCode));
      }
    }
    error(statusCode, error, customizer);
  }

  static String spanNameFromRoute(String route, int statusCode) {
    if (route == null) return null; // don't undo a valid name elsewhere
    if (!"".equals(route)) return route;
    if (statusCode / 100 == 3) return "redirected";
    if (statusCode == 404) return "not_found";
    return null; // unexpected
  }

  /**
   * Override to change what data from the http error are parsed into the span modeling it. By
   * default, this tags "error" as the exception or the status code if it is neither 2xx nor 3xx.
   *
   * <p>Note: Either the httpStatus or error parameters may be null, but not both
   *
   * <p>Conventionally associated with the tag key "error"
   */
  // BRAVE5: httpStatus is a Integer, not a int. We can't change this api as users expect this to be
  // called by default. Unfortunately, this implies boxing until we can change it.
  protected void error(@Nullable Integer httpStatus, @Nullable Throwable error,
      SpanCustomizer customizer) {
    String message = null;
    if (error != null) {
      message = error.getMessage();
      if (message == null) message = error.getClass().getSimpleName();
    } else if (httpStatus != null && httpStatus != 0) {
      message = httpStatus < 200 || httpStatus > 399 ? String.valueOf(httpStatus) : null;
    }
    if (message != null) customizer.tag("error", message);
  }

  HttpParser() {
  }
}

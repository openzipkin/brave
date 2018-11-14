package brave.http;

import brave.SpanCustomizer;
import brave.internal.Nullable;

/**
 * Parses the request and response into reasonable defaults for http client spans. Subclass to
 * customize, for example, to add tags based on response headers.
 */
public class HttpClientParser extends HttpParser {

  /**
   * Customizes the span based on the request that will be sent to the server.
   * Add "http.url" default tag with requested URL.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
      SpanCustomizer customizer) {
    super.request(adapter, req, customizer);

    String url = adapter.url(req);
    if (url != null) customizer.tag("http.url", url);
  }

  /**
   * Customizes the span based on the response received from the server.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Resp> void response(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, SpanCustomizer customizer) {
    super.response(adapter, res, error, customizer);
  }
}

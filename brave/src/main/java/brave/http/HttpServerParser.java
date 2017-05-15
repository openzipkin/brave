package brave.http;

import brave.SpanCustomizer;
import brave.internal.Nullable;

/**
 * Parses the request and response into reasonable defaults for http server spans. Subclass to
 * customize, for example, to add tags based on user ID.
 */
public class HttpServerParser extends HttpParser {

  /**
   * Customizes the span based on the request received from the client.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
      SpanCustomizer customizer) {
    super.request(adapter, req, customizer);
  }

  /**
   * Customizes the span based on the response sent to the client.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Resp> void response(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, SpanCustomizer customizer) {
    super.response(adapter, res, error, customizer);
  }
}

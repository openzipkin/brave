package brave.http;

import brave.Span;
import brave.internal.Nullable;

/**
 * Provides reasonable defaults for the data contained in http client spans. Subclass to customize,
 * for example, to add tags based on response headers.
 */
public class HttpClientParser extends HttpParser {

  /**
   * Adds any tags based on the request that will be sent to the server.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, Span span) {
    super.requestTags(adapter, req, span);
  }

  /**
   * Adds any tags based on the response received from the server.
   *
   * <p>{@inheritDoc}
   */
  @Override public <Resp> void responseTags(HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, Span span) {
    super.responseTags(adapter, res, error, span);
  }
}

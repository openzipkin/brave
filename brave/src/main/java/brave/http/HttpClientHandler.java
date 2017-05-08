package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;

/**
 * This standardizes a way to instrument http clients, particularly in a way that encourages use of
 * portable customizations via {@link HttpClientParser}.
 *
 * @param <Req> the native http request type of the client.
 * @param <Resp> the native http response type of the client.
 */
public final class HttpClientHandler<Req, Resp> {
  public static <Req, Resp> HttpClientHandler create(HttpAdapter<Req, Resp> adapter,
      HttpClientParser parser) {
    return new HttpClientHandler<>(adapter, parser);
  }

  final HttpAdapter<Req, Resp> adapter;
  final HttpClientParser parser;

  HttpClientHandler(HttpAdapter<Req, Resp> adapter, HttpClientParser parser) {
    this.adapter = adapter;
    this.parser = parser;
  }

  /**
   * Starts the client span after assigning it a name and tags.
   *
   * <p>This is typically called after a span is {@link TraceContext.Injector#inject(TraceContext,
   * Object) injected} onto the request, and before request is processed by the actual library.
   */
  public void handleSend(Req request, Span span) {
    if (span.isNoop()) return;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.CLIENT).name(parser.spanName(adapter, request));
    parser.requestTags(adapter, request, span);
    span.start();
  }

  /**
   * Finishes the client span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are received, and after the span is
   * {@link brave.Tracer.SpanInScope#close() no longer in scope}.
   */
  public void handleReceive(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span.isNoop()) return;

    try {
      if (error != null) {
        String message = error.getMessage();
        if (message == null) message = error.getClass().getSimpleName();
        span.tag(zipkin.Constants.ERROR, message);
      }
      if (response != null) parser.responseTags(adapter, response, span);
    } finally {
      span.finish();
    }
  }
}

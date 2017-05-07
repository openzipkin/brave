package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;

/**
 * This standardizes a way to instrument http servers, particularly in a way that encourages use of
 * portable customizations via {@link HttpServerParser}.
 *
 * @param <Req> the native http request type of the server.
 * @param <Resp> the native http response type of the server.
 */
public final class HttpServerHandler<Req, Resp> {
  public static <Req, Resp> HttpServerHandler create(HttpAdapter<Req, Resp> adapter,
      HttpServerParser parser) {
    return new HttpServerHandler<>(adapter, parser);
  }

  final HttpAdapter<Req, Resp> adapter;
  final HttpServerParser parser;

  HttpServerHandler(HttpAdapter<Req, Resp> adapter, HttpServerParser parser) {
    this.adapter = adapter;
    this.parser = parser;
  }

  /**
   * Starts the server span after assigning it a name and tags.
   *
   * <p>This is typically called after a span is {@link brave.Tracer#nextSpan(TraceContext.Extractor,
   * Object) extracted} from the request and before the request is processed by the actual library.
   */
  public void handleReceive(Req request, Span span) {
    if (span.isNoop()) return;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER).name(parser.spanName(adapter, request));
    parser.requestTags(adapter, request, span);
    span.start();
  }

  /**
   * Finishes the server span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are sent, and after the span is
   * {@link brave.Tracer.SpanInScope#close() no longer in scope}.
   */
  public void handleSend(@Nullable Resp response, @Nullable Throwable error, Span span) {
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

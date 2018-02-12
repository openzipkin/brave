package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import zipkin2.Endpoint;

/**
 * This standardizes a way to instrument http servers, particularly in a way that encourages use of
 * portable customizations via {@link HttpServerParser}.
 *
 * <p>This is an example of synchronous instrumentation:
 * <pre>{@code
 * Span span = handler.handleReceive(extractor, request);
 * Throwable error = null;
 * try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
 *   response = invoke(request); // any downstream code can see Tracer.currentSpan
 * } catch (RuntimeException | Error e) {
 *   error = e;
 *   throw e;
 * } finally {
 *   handler.handleSend(response, error, span);
 * }
 * }</pre>
 *
 * @param <Req> the native http request type of the server.
 * @param <Resp> the native http response type of the server.
 */
public final class HttpServerHandler<Req, Resp> {

  public static <Req, Resp> HttpServerHandler create(HttpTracing httpTracing,
      HttpServerAdapter<Req, Resp> adapter) {
    return new HttpServerHandler<>(
        httpTracing.tracing().tracer(),
        httpTracing.serverSampler(),
        httpTracing.serverParser(),
        adapter
    );
  }

  final Tracer tracer;
  final HttpSampler sampler;
  final HttpServerParser parser;
  final HttpServerAdapter<Req, Resp> adapter;

  HttpServerHandler(
      Tracer tracer,
      HttpSampler sampler,
      HttpServerParser parser,
      HttpServerAdapter<Req, Resp> adapter
  ) {
    this.tracer = tracer;
    this.sampler = sampler;
    this.parser = parser;
    this.adapter = adapter;
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   */
  public Span handleReceive(TraceContext.Extractor<Req> extractor, Req request) {
    return handleReceive(extractor, request, request);
  }

  /**
   * Like {@link #handleReceive(TraceContext.Extractor, Object)}, except for when the carrier of
   * trace data is not the same as the request.
   *
   * <p>Request data is parsed before the span is started.
   *
   * @see HttpServerParser#request(HttpAdapter, Object, SpanCustomizer)
   */
  public <C> Span handleReceive(TraceContext.Extractor<C> extractor, C carrier, Req request) {
    Span span = nextSpan(extractor.extract(carrier), request);
    if (span.isNoop()) return span;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER);
    parseRequest(request, span);

    return span.start();
  }

  void parseRequest(Req request, Span span) {
    if (span.isNoop()) return;
    // Ensure user-code can read the current trace context
    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    try {
      parser.request(adapter, request, span);
    } finally {
      ws.close();
    }

    Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
    if (adapter.parseClientAddress(request, remoteEndpoint)) {
      span.remoteEndpoint(remoteEndpoint.build());
    }
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(TraceContextOrSamplingFlags extracted, Req request) {
    if (extracted.sampled() == null) { // Otherwise, try to make a new decision
      extracted = extracted.sampled(sampler.trySample(adapter, request));
    }
    return extracted.context() != null
        ? tracer.joinSpan(extracted.context())
        : tracer.nextSpan(extracted);
  }

  /**
   * Finishes the server span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are sent, and after the span is {@link
   * brave.Tracer.SpanInScope#close() no longer in scope}.
   *
   * @see HttpServerParser#response(HttpAdapter, Object, Throwable, SpanCustomizer)
   */
  public void handleSend(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span.isNoop()) return;

    // Ensure user-code can read the current trace context
    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    try {
      parser.response(adapter, response, error, span);
    } finally {
      ws.close();
      span.finish();
    }
  }
}

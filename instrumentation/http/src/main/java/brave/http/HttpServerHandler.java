package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.MutableTraceContext;
import brave.propagation.TraceContext;

/**
 * This standardizes a way to instrument http servers, particularly in a way that encourages use of
 * portable customizations via {@link HttpServerParser}.
 *
 * <p>This is an example of synchronous instrumentation:
 * <pre>{@code
 * Span span = handler.handleReceive(extractor, request);
 * Throwable error = null;
 * try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
 *   // any downstream code can see Tracer.currentSpan() or use Tracer.currentSpanCustomizer()
 *   response = invoke(request);
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
public final class HttpServerHandler<Req, Resp>
    extends HttpHandler<Req, Resp, HttpServerAdapter<Req, Resp>> {

  public static <Req, Resp> HttpServerHandler<Req, Resp> create(HttpTracing httpTracing,
      HttpServerAdapter<Req, Resp> adapter) {
    return new HttpServerHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final HttpSampler sampler;

  HttpServerHandler(HttpTracing httpTracing, HttpServerAdapter<Req, Resp> adapter) {
    super(
        httpTracing.tracing().currentTraceContext(),
        adapter,
        httpTracing.serverParser()
    );
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.serverSampler();
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   */
  @Deprecated
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
  @Deprecated
  public <C> Span handleReceive(TraceContext.Extractor<C> extractor, C carrier, Req request) {
    Span span = nextSpan(MutableTraceContext.create(extractor.extract(carrier)), request);
    return handleStart(request, span);
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   */
  public Span handleReceive(MutableTraceContext.Extractor<Req> extractor, Req request) {
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
  public <C> Span handleReceive(MutableTraceContext.Extractor<C> extractor, C carrier,
      Req request) {
    MutableTraceContext context = new MutableTraceContext();
    extractor.extract(carrier, context);
    Span span = nextSpan(context, request);
    return handleStart(request, span);
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(MutableTraceContext extracted, Req request) {
    if (extracted.sampled() == null) { // Otherwise, try to make a new decision
      Boolean sampled = sampler.trySample(adapter, request);
      if (sampled != null) extracted.sampled(sampled);
    }
    return tracer.joinSpan(extracted);
  }

  @Override void parseRequest(Req request, Span span) {
    span.kind(Span.Kind.SERVER);
    adapter.parseClientIpAndPort(request, span);
    parser.request(adapter, request, span.customizer());
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
    handleFinish(response, error, span);
  }
}

package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
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
    return new HttpServerHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final HttpSampler sampler;
  final HttpServerParser parser;
  final HttpServerAdapter<Req, Resp> adapter;

  HttpServerHandler(HttpTracing httpTracing, HttpServerAdapter<Req, Resp> adapter) {
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.serverSampler();
    this.parser = httpTracing.serverParser();
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
   * @see HttpServerParser#request(HttpAdapter, Object, SpanCustomizer)
   */
  public <C> Span handleReceive(TraceContext.Extractor<C> extractor, C carrier, Req request) {
    Span span = nextSpan(extractor.extract(carrier), request);
    if (span.isNoop()) return span;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER);
    parser.request(adapter, request, span);

    boolean parsedEndpoint = false;
    if (Platform.get().zipkinV1Present()) {
      zipkin.Endpoint.Builder deprecatedEndpoint = zipkin.Endpoint.builder().serviceName("");
      if ((parsedEndpoint = adapter.parseClientAddress(request, deprecatedEndpoint))) {
        span.remoteEndpoint(deprecatedEndpoint.build());
      }
    }
    if (!parsedEndpoint) {
      Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
      if (adapter.parseClientAddress(request, remoteEndpoint)) {
        span.remoteEndpoint(remoteEndpoint.build());
      }
    }
    return span.start();
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(TraceContextOrSamplingFlags extracted, Req request) {
    // First, check to see if a complete context is present
    TraceContext context = extracted.context();
    if (context != null) {
      // If there were trace IDs in the request and a sampling decision, honor it
      if (context.sampled() != null) return tracer.joinSpan(context);

      // Otherwise, try to make a new decision
      return tracer.joinSpan(context.toBuilder()
          .sampled(sampler.trySample(adapter, request))
          .build());
    }

    // Next, check to see if trace IDs, but not span IDs are present
    TraceIdContext traceIdContext = extracted.traceIdContext();
    if (traceIdContext != null) {
      if (traceIdContext.sampled() != null) {
        return tracer.newTrace(traceIdContext);
      }

      // Otherwise, try to make a new decision
      return tracer.newTrace(traceIdContext.toBuilder()
          .sampled(sampler.trySample(adapter, request))
          .build());
    }

    // There was no trace ID in the incoming requests. However, there might be sampling flags
    SamplingFlags samplingFlags = extracted.samplingFlags();
    if (samplingFlags.sampled() == null) {
      samplingFlags = new SamplingFlags.Builder()
          .sampled(sampler.trySample(adapter, request))
          .debug(samplingFlags.debug()) // should always be false if unsampled!
          .build();
    }
    return tracer.newTrace(samplingFlags);
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
    try {
      parser.response(adapter, response, error, span);
    } finally {
      span.finish();
    }
  }
}

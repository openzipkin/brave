package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.annotation.Nullable;
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

    zipkin.Endpoint.Builder deprecatedEndpoint = zipkin.Endpoint.builder().serviceName("");
    if (adapter.parseClientAddress(request, deprecatedEndpoint)) {
      span.remoteEndpoint(deprecatedEndpoint.build());
    } else {
      Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
      if (adapter.parseClientAddress(request, remoteEndpoint)) {
        span.remoteEndpoint(remoteEndpoint.build());
      }
    }
    return span.start();
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(TraceContextOrSamplingFlags contextOrFlags, Req request) {
    TraceContext extracted = contextOrFlags.context();
    if (extracted != null) {
      // If there were trace IDs in the request and a sampling decision, honor it
      if (extracted.sampled() != null) return tracer.joinSpan(contextOrFlags.context());

      // Otherwise, try to make a new decision
      return tracer.joinSpan(extracted.toBuilder()
          .sampled(sampler.trySample(adapter, request))
          .build());
    }

    // There was no trace in the incoming requests. However, there might be sampling flags
    SamplingFlags flags = contextOrFlags.samplingFlags();
    if (flags.sampled() == null) {
      flags = new SamplingFlags.Builder()
          .sampled(sampler.trySample(adapter, request))
          .debug(flags.debug()) // should always be false if unsampled!
          .build();
    }
    return tracer.newTrace(flags);
  }

  /**
   * Finishes the server span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are sent, and after the span is
   * {@link brave.Tracer.SpanInScope#close() no longer in scope}.
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

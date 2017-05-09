package brave.http;

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import zipkin.Endpoint;

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

  final HttpServerParser parser;
  final Tracer tracer;
  final HttpServerAdapter<Req, Resp> adapter;

  HttpServerHandler(HttpTracing httpTracing, HttpServerAdapter<Req, Resp> adapter) {
    this.parser = httpTracing.serverParser();
    this.tracer = httpTracing.tracing().tracer();
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
   */
  public <C> Span handleReceive(TraceContext.Extractor<C> extractor, C carrier, Req request) {
    TraceContextOrSamplingFlags contextOrFlags = extractor.extract(carrier);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    if (span.isNoop()) return span;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER).name(parser.spanName(adapter, request));
    parser.requestTags(adapter, request, span);
    Endpoint.Builder remoteEndpoint = Endpoint.builder();
    if (adapter.parseClientAddress(request, remoteEndpoint)) {
      span.remoteEndpoint(remoteEndpoint.serviceName("").build());
    }
    return span.start();
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
      if (response != null || error != null) {
        String message = adapter.parseError(response, error);
        if (message != null) span.tag(zipkin.Constants.ERROR, message);
      }
      if (response != null) parser.responseTags(adapter, response, span);
    } finally {
      span.finish();
    }
  }
}

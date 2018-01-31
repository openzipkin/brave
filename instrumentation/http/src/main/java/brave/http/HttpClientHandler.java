package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;

/**
 * This standardizes a way to instrument http clients, particularly in a way that encourages use of
 * portable customizations via {@link HttpClientParser}.
 *
 * <p>This is an example of synchronous instrumentation:
 * <pre>{@code
 * Span span = handler.handleSend(injector, request);
 * Throwable error = null;
 * try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
 *   response = invoke(request); // any downstream code can see Tracer.currentSpan
 * } catch (RuntimeException | Error e) {
 *   error = e;
 *   throw e;
 * } finally {
 *   handler.handleReceive(response, error, span);
 * }
 * }</pre>
 *
 * @param <Req> the native http request type of the client.
 * @param <Resp> the native http response type of the client.
 * @since 4.3
 */
public final class HttpClientHandler<Req, Resp> {

  public static <Req, Resp> HttpClientHandler create(HttpTracing httpTracing,
      HttpClientAdapter<Req, Resp> adapter) {
    return new HttpClientHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final HttpSampler sampler;
  final CurrentTraceContext currentTraceContext;
  final HttpClientParser parser;
  final HttpClientAdapter<Req, Resp> adapter;
  final String serverName;
  final boolean serverNameSet;

  HttpClientHandler(HttpTracing httpTracing, HttpClientAdapter<Req, Resp> adapter) {
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.clientSampler();
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    this.parser = httpTracing.clientParser();
    this.serverName = httpTracing.serverName();
    this.serverNameSet = !serverName.equals("");
    this.adapter = adapter;
  }

  /**
   * Starts the client span after assigning it a name and tags. This {@link
   * TraceContext.Injector#inject(TraceContext, Object) injects} the trace context onto the request
   * before returning.
   *
   * <p>Call this before sending the request on the wire.
   */
  public Span handleSend(TraceContext.Injector<Req> injector, Req request) {
    return handleSend(injector, request, request);
  }

  /**
   * Like {@link #handleSend(TraceContext.Injector, Object)}, except for when the carrier of
   * trace data is not the same as the request.
   *
   * @see HttpClientParser#request(HttpAdapter, Object, SpanCustomizer)
   */
  public <C> Span handleSend(TraceContext.Injector<C> injector, C carrier, Req request) {
    return handleSend(injector, carrier, request, nextSpan(request));
  }

  /**
   * Like {@link #handleSend(TraceContext.Injector, Object)}, except explicitly controls the span
   * representing the request.
   *
   * @since 4.4
   */
  public Span handleSend(TraceContext.Injector<Req> injector, Req request, Span span) {
    return handleSend(injector, request, request, span);
  }

  /**
   * Like {@link #handleSend(TraceContext.Injector, Object, Object)}, except explicitly controls the
   * span representing the request.
   *
   * @since 4.4
   */
  public <C> Span handleSend(TraceContext.Injector<C> injector, C carrier, Req request, Span span) {
    injector.inject(span.context(), carrier);
    if (span.isNoop()) return span;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.CLIENT);

    // Ensure user-code can read the current trace context
    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    try {
      parser.request(adapter, request, span);
    } finally {
      ws.close();
    }

    Endpoint.Builder remoteEndpoint = Endpoint.newBuilder().serviceName(serverName);
    if (adapter.parseServerAddress(request, remoteEndpoint) || serverNameSet) {
      span.remoteEndpoint(remoteEndpoint.build());
    }
    return span.start();
  }

  /**
   * Creates a potentially noop span representing this request. This is used when you need to
   * provision a span in a different scope than where the request is executed.
   *
   * @since 4.4
   */
  public Span nextSpan(Req request) {
    TraceContext parent = currentTraceContext.get();
    if (parent != null) return tracer.newChild(parent); // inherit the sampling decision

    // If there was no parent, we are making a new trace. Try to sample the request.
    Boolean sampled = sampler.trySample(adapter, request);
    if (sampled == null) return tracer.newTrace(); // defer sampling decision to trace ID
    return tracer.newTrace(sampled ? SamplingFlags.SAMPLED : SamplingFlags.NOT_SAMPLED);
  }

  /**
   * Finishes the client span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are received, and after the span is
   * {@link brave.Tracer.SpanInScope#close() no longer in scope}.
   *
   * @see HttpClientParser#response(HttpAdapter, Object, Throwable, SpanCustomizer)
   */
  public void handleReceive(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span.isNoop()) return;
    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    try {
      parser.response(adapter, response, error, span);
    } finally {
      ws.close();
      span.finish();
    }
  }
}

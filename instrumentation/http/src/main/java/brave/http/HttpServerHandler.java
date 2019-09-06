/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

/**
 * This standardizes a way to instrument http servers, particularly in a way that encourages use of
 * portable customizations via {@link HttpServerParser}.
 *
 * <p>This is an example of synchronous instrumentation:
 * <pre>{@code
 * Span span = handler.handleReceive(request);
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
public final class HttpServerHandler<Req, Resp> extends HttpHandler {
  /** @since 5.7 */
  public static HttpServerHandler<HttpServerRequest, HttpServerResponse> create(
    HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    return new HttpServerHandler<>(httpTracing, null);
  }

  /** @deprecated Since 5.7, use {@link #create(HttpTracing)} as it is more portable. */
  @Deprecated
  public static <Req, Resp> HttpServerHandler<Req, Resp> create(HttpTracing httpTracing,
    HttpServerAdapter<Req, Resp> adapter) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    if (adapter == null) throw new NullPointerException("adapter == null");
    return new HttpServerHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final HttpSampler sampler;
  @Nullable final HttpServerAdapter<Req, Resp> adapter; // null when using default types
  final TraceContext.Extractor<HttpServerRequest> defaultExtractor;

  HttpServerHandler(HttpTracing httpTracing, HttpServerAdapter<Req, Resp> adapter) {
    super(
      httpTracing.tracing().currentTraceContext(),
      httpTracing.serverParser()
    );
    this.adapter = adapter;
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.serverSampler();
    // The following allows us to add the method: handleReceive(HttpServerRequest request) without
    // duplicating logic from the superclass or deprecated handleReceive methods.
    this.defaultExtractor = httpTracing.tracing().propagation().extractor(HttpServerRequest.GETTER);
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   *
   * @since 5.7
   */
  public Span handleReceive(HttpServerRequest request) {
    HttpServerRequest.Adapter adapter = new HttpServerRequest.Adapter(request);
    Span span = nextSpan(adapter, defaultExtractor.extract(request), adapter.unwrapped);
    return handleStart(adapter, adapter.unwrapped, span);
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   *
   * @deprecated Since 5.7, use {@link #handleReceive(HttpServerRequest)}, as this allows more
   * advanced samplers to be used.
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
   * @deprecated Since 5.7, use {@link #handleReceive(HttpServerRequest)} to handle any difference
   * between carrier and request via wrapping in {@link HttpServerRequest}.
   */
  @Deprecated
  public <C> Span handleReceive(TraceContext.Extractor<C> extractor, C carrier, Req request) {
    Span span = nextSpan(adapter, extractor.extract(carrier), request);
    return handleStart(adapter, request, span);
  }

  @Override <Req1> void parseRequest(HttpAdapter<Req1, ?> adapter, Req1 request, Span span) {
    span.kind(Span.Kind.SERVER);
    ((HttpServerAdapter<Req1, ?>) adapter).parseClientIpAndPort(request, span);
    parser.request(adapter, request, span.customizer());
  }

  /** Creates a potentially noop span representing this request */
  <Req1> Span nextSpan(HttpAdapter<Req1, ?> adapter, TraceContextOrSamplingFlags extracted,
    Req1 request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the http sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(adapter, request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
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
    if (response instanceof HttpServerResponse) {
      HttpServerResponse.Adapter adapter =
        new HttpServerResponse.Adapter((HttpServerResponse) response);
      handleFinish(adapter, adapter.unwrapped, error, span);
    } else {
      handleFinish(adapter, response, error, span);
    }
  }
}

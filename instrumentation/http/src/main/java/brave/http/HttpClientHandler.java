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
import brave.propagation.TraceContext.Injector;
import brave.sampler.Sampler;

/**
 * This standardizes a way to instrument http clients, particularly in a way that encourages use of
 * portable customizations via {@link HttpClientParser}.
 *
 * <p>This is an example of synchronous instrumentation:
 * <pre>{@code
 * Span span = handler.handleSend(request);
 * Throwable error = null;
 * try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
 *   // any downstream code can see Tracer.currentSpan() or use Tracer.currentSpanCustomizer()
 *   response = invoke(request);
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
public final class HttpClientHandler<Req, Resp>
  extends HttpHandler<Req, Resp, HttpClientAdapter<Req, Resp>> {

  /** @since 5.7 */
  public static HttpClientHandler<HttpClientRequest, HttpClientResponse> create(
    HttpTracing httpTracing) {
    return new HttpClientHandler<>(httpTracing, HttpClientAdapter.LEGACY);
  }

  /** @deprecated Since 5.7, use {@link #create(HttpTracing)} as it is more portable. */
  @Deprecated
  public static <Req, Resp> HttpClientHandler<Req, Resp> create(HttpTracing httpTracing,
    HttpClientAdapter<Req, Resp> adapter) {
    return new HttpClientHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final Sampler sampler;
  final HttpSampler httpSampler;
  @Nullable final String serverName;
  final Injector<HttpClientRequest> defaultInjector;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> defaultHandler;

  HttpClientHandler(HttpTracing httpTracing, HttpClientAdapter<Req, Resp> adapter) {
    super(
      httpTracing.tracing().currentTraceContext(),
      adapter,
      httpTracing.clientParser()
    );
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.tracing().sampler();
    this.httpSampler = httpTracing.clientSampler();
    this.serverName = !"".equals(httpTracing.serverName()) ? httpTracing.serverName() : null;
    // The following allows us to add the method: handleSend(HttpClientRequest request) without
    // duplicating logic from the superclass or deprecated handleReceive methods.
    this.defaultInjector = httpTracing.tracing().propagation().injector(HttpClientRequest.SETTER);
    this.defaultHandler = adapter == HttpClientAdapter.LEGACY // special casing prevents recursion
      ? (HttpClientHandler<HttpClientRequest, HttpClientResponse>) this
      : new HttpClientHandler<>(httpTracing, HttpClientAdapter.LEGACY);
  }

  /**
   * Starts the client span after assigning it a name and tags. This {@link
   * Injector#inject(TraceContext, Object) injects} the trace context onto the request before
   * returning.
   *
   * <p>Call this before sending the request on the wire.
   *
   * @since 5.7
   */
  public Span handleSend(HttpClientRequest request) {
    return handleSend(request, defaultHandler.nextSpan(request));
  }

  /**
   * Like {@link #handleSend(HttpClientRequest)}, except explicitly controls the span representing
   * the request.
   *
   * @since 5.7
   */
  public Span handleSend(HttpClientRequest request, Span span) {
    return defaultHandler.handleSend(defaultInjector, request, request, span);
  }

  /**
   * Starts the client span after assigning it a name and tags. This {@link
   * Injector#inject(TraceContext, Object) injects} the trace context onto the request before
   * returning.
   *
   * <p>Call this before sending the request on the wire.
   *
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)}, as this allows more advanced
   * samplers to be used.
   */
  @Deprecated public Span handleSend(Injector<Req> injector, Req request) {
    return handleSend(injector, request, request);
  }

  /**
   * Like {@link #handleSend(Injector, Object)}, except for when the carrier of trace data is not
   * the same as the request.
   *
   * @see HttpClientParser#request(HttpAdapter, Object, SpanCustomizer)
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)} to handle any difference
   * between carrier and request via wrapping in {@link HttpClientRequest}.
   */
  @Deprecated public <C> Span handleSend(Injector<C> injector, C carrier, Req request) {
    return handleSend(injector, carrier, request, nextSpan(request));
  }

  /**
   * Like {@link #handleSend(Injector, Object)}, except explicitly controls the span representing
   * the request.
   *
   * @since 4.4
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)}, as this allows more advanced
   * samplers to be used.
   */
  @Deprecated public Span handleSend(Injector<Req> injector, Req request, Span span) {
    return handleSend(injector, request, request, span);
  }

  /**
   * Like {@link #handleSend(Injector, Object, Object)}, except explicitly controls the span
   * representing the request.
   *
   * @since 4.4
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)} to handle any difference
   * between carrier and request via wrapping in {@link HttpClientRequest}.
   */
  @Deprecated public <C> Span handleSend(Injector<C> injector, C carrier, Req request, Span span) {
    injector.inject(span.context(), carrier);
    return handleStart(request, span);
  }

  @Override void parseRequest(Req request, Span span) {
    span.kind(Span.Kind.CLIENT);
    if (serverName != null) span.remoteServiceName(serverName);
    parser.request(adapter, request, span.customizer());
  }

  /**
   * Creates a potentially noop span representing this request. This is used when you need to
   * provision a span in a different scope than where the request is executed.
   *
   * @since 4.4
   */
  public Span nextSpan(Req request) {
    Sampler override = httpSampler.toSampler(adapter, request, sampler);
    return tracer.withSampler(override).nextSpan();
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
    handleFinish(response, error, span);
  }
}

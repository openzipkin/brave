/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import brave.http.HttpServerAdapters.ToResponseAdapter;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;

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
 * @since 4.3
 */
public final class HttpServerHandler<Req, Resp> extends HttpHandler {
  /** @since 5.7 */
  public static HttpServerHandler<HttpServerRequest, HttpServerResponse> create(
    HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    return new HttpServerHandler<>(httpTracing, null);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #create(HttpTracing)} as it is more portable.
   */
  @Deprecated
  public static <Req, Resp> HttpServerHandler<Req, Resp> create(HttpTracing httpTracing,
    HttpServerAdapter<Req, Resp> adapter) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    if (adapter == null) throw new NullPointerException("adapter == null");
    return new HttpServerHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  final SamplerFunction<HttpRequest> sampler;
  @Nullable final HttpServerAdapter<Req, Resp> adapter; // null when using default types
  final Extractor<HttpServerRequest> defaultExtractor;

  HttpServerHandler(HttpTracing httpTracing, HttpServerAdapter<Req, Resp> adapter) {
    super(
      httpTracing.tracing().currentTraceContext(),
      httpTracing.serverParser()
    );
    this.adapter = adapter;
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.serverRequestSampler();
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
    Span span = nextSpan(defaultExtractor.extract(request), request);

    Object unwrapped = request.unwrap();
    if (unwrapped == null) unwrapped = NULL_SENTINEL; // Ensure adapter methods never see null
    HttpAdapter<Object, Void> adapter = new HttpServerAdapters.ToRequestAdapter(request, unwrapped);

    return handleStart(adapter, unwrapped, span);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #handleReceive(HttpServerRequest)}
   */
  @Deprecated public Span handleReceive(Extractor<Req> extractor, Req request) {
    return handleReceive(extractor, request, request);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #handleReceive(HttpServerRequest)}
   */
  @Deprecated public <C> Span handleReceive(Extractor<C> extractor, C carrier, Req request) {
    HttpServerRequest serverRequest;
    if (request instanceof HttpServerRequest) {
      serverRequest = (HttpServerRequest) request;
    } else {
      serverRequest = new HttpServerAdapters.FromRequestAdapter<>(adapter, request);
    }
    Span span = nextSpan(extractor.extract(carrier), serverRequest);
    return handleStart(adapter, request, span);
  }

  @Override <Req1> void parseRequest(HttpAdapter<Req1, ?> adapter, Req1 request, Span span) {
    span.kind(Span.Kind.SERVER);
    ((HttpServerAdapter<Req1, ?>) adapter).parseClientIpAndPort(request, span);
    parser.request(adapter, request, span.customizer());
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(TraceContextOrSamplingFlags extracted, HttpServerRequest request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the http sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
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
   * <p>Note: Either the response or error parameters may be null, but not both.
   *
   * @see HttpServerParser#response(HttpAdapter, Object, Throwable, SpanCustomizer)
   * @since 4.3
   */
  public void handleSend(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (response == null && error == null) {
      throw new IllegalArgumentException(
        "Either the response or error parameters may be null, but not both");
    }

    if (!(response instanceof HttpServerResponse)) {
      handleFinish(adapter, response, error, span);
      return;
    }

    HttpServerResponse serverResponse = (HttpServerResponse) response;
    Object unwrapped = serverResponse.unwrap();
    if (unwrapped == null) unwrapped = NULL_SENTINEL; // Ensure adapter methods never see null

    handleFinish(new ToResponseAdapter(serverResponse, unwrapped), unwrapped, error, span);
  }
}

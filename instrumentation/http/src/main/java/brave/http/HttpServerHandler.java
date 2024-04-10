/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;

/**
 * This standardizes a way to instrument http servers, particularly in a way that encourages use of
 * portable customizations via {@link HttpRequestParser} and {@link HttpResponseParser}.
 *
 * <p>Synchronous interception is the most straight forward instrumentation.
 *
 * <p>You generally need to:
 * <ol>
 *   <li>Extract any trace IDs from headers and start the span</li>
 *   <li>Put the span in scope so things like log integration works</li>
 *   <li>Process the request</li>
 *   <li>If there was a Throwable, add it to the span</li>
 *   <li>Complete the span</li>
 * </ol>
 * <pre>{@code
 * HttpServerRequestWrapper requestWrapper = new HttpServerRequestWrapper(request);
 * Span span = handler.handleReceive(requestWrapper); // 1.
 * HttpServerResponse response = null;
 * Throwable error = null;
 * try (Scope scope = currentTraceContext.newScope(span.context())) { // 2.
 *   return response = process(request); // 3.
 * } catch (Throwable e) {
 *   error = e; // 4.
 *   throw e;
 * } finally {
 *   HttpServerResponseWrapper responseWrapper =
 *     new HttpServerResponseWrapper(requestWrapper, response, error);
 *   handler.handleSend(responseWrapper, span); // 5.
 * }
 * }</pre>
 *
 * @param <Req> the native http request type of the server.
 * @param <Resp> the native http response type of the server.
 * @since 4.3
 */
// The generic type parameters should always be <HttpServerRequest, HttpServerResponse>. Even if the
// deprecated methods are removed in Brave v6, we should not remove these parameters as that would
// cause a compilation break and lead to revlock.
public final class HttpServerHandler<Req, Resp> extends HttpHandler {
  /** @since 5.7 */
  public static HttpServerHandler<HttpServerRequest, HttpServerResponse> create(
    HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    return new HttpServerHandler<HttpServerRequest, HttpServerResponse>(httpTracing);
  }


  final Tracer tracer;
  final SamplerFunction<HttpRequest> sampler;
  final Extractor<HttpServerRequest> defaultExtractor;

  HttpServerHandler(HttpTracing httpTracing) {
    super(httpTracing.serverRequestParser(), httpTracing.serverResponseParser());
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.serverRequestSampler();
    // The following allows us to add the method: handleReceive(HttpServerRequest request) without
    // duplicating logic from the superclass or deprecated handleReceive methods.
    this.defaultExtractor = httpTracing.propagation().extractor(HttpServerRequest.GETTER);
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   *
   * @see HttpTracing#serverRequestSampler()
   * @see HttpTracing#serverRequestParser()
   * @since 5.7
   */
  public Span handleReceive(HttpServerRequest request) {
    Span span = nextSpan(defaultExtractor.extract(request), request);
    return handleStart(request, span);
  }

  @Override void parseRequest(HttpRequest request, Span span) {
    ((HttpServerRequest) request).parseClientIpAndPort(span);
    super.parseRequest(request, span);
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
   * SpanInScope#close() no longer in scope}.
   *
   * <p><em>Note</em>: It is valid to have a {@link HttpServerResponse} that only includes an
   * {@linkplain HttpServerResponse#error() error}. However, it is better to also include the
   * {@linkplain HttpServerResponse#request() request}.
   *
   * @see HttpResponseParser#parse(HttpResponse, TraceContext, SpanCustomizer)
   * @since 5.12
   */
  public void handleSend(HttpServerResponse response, Span span) {
    handleFinish(response, span);
  }
}

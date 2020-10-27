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
import brave.Tracer.SpanInScope;
import brave.http.HttpServerAdapters.FromResponseAdapter;
import brave.internal.Nullable;
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
 * try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
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
  @Deprecated @Nullable final HttpServerAdapter<Req, Resp> adapter; // null when using default types
  final Extractor<HttpServerRequest> defaultExtractor;

  HttpServerHandler(HttpTracing httpTracing, @Deprecated HttpServerAdapter<Req, Resp> adapter) {
    super(httpTracing.serverRequestParser(), httpTracing.serverResponseParser());
    this.adapter = adapter;
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
    if (carrier == null) throw new NullPointerException("request == null");

    HttpServerRequest serverRequest;
    if (request instanceof HttpServerRequest) {
      serverRequest = (HttpServerRequest) request;
    } else {
      serverRequest = new HttpServerAdapters.FromRequestAdapter<>(adapter, request);
    }

    Span span = nextSpan(extractor.extract(carrier), serverRequest);
    return handleStart(serverRequest, span);
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
   * @since 4.3
   * @deprecated Since 5.12, use {@link #handleSend(HttpServerResponse, Span)}
   */
  public void handleSend(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span == null) throw new NullPointerException("span == null");
    if (response == null && error == null) {
      throw new IllegalArgumentException(
        "Either the response or error parameters may be null, but not both");
    }

    if (response == null) {
      span.error(error).finish();
      return;
    }

    HttpServerResponse serverResponse;
    if (response instanceof HttpServerResponse) {
      serverResponse = (HttpServerResponse) response;
      if (serverResponse.error() == null && error != null) {
        span.error(error);
      }
    } else {
      serverResponse = new FromResponseAdapter<>(adapter, response, error);
    }
    handleFinish(serverResponse, span);
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

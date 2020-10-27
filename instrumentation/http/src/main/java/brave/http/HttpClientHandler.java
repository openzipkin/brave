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
import brave.http.HttpClientAdapters.FromRequestAdapter;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;

import static brave.http.HttpClientAdapters.FromResponseAdapter;

/**
 * This standardizes a way to instrument http clients, particularly in a way that encourages use of
 * portable customizations via {@link HttpRequestParser} and {@link HttpResponseParser}.
 *
 * <p>Synchronous interception is the most straight forward instrumentation.
 *
 * <p>You generally need to:
 * <ol>
 *   <li>Start the span and add trace headers to the request</li>
 *   <li>Put the span in scope so things like log integration works</li>
 *   <li>Invoke the request</li>
 *   <li>If there was a Throwable, add it to the span</li>
 *   <li>Complete the span</li>
 * </ol>
 *
 * <pre>{@code
 * HttpClientRequestWrapper requestWrapper = new HttpClientRequestWrapper(request);
 * Span span = handler.handleSend(requestWrapper); // 1.
 * HttpClientResponse response = null;
 * Throwable error = null;
 * try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
 *   return response = invoke(request); // 3.
 * } catch (Throwable e) {
 *   error = e; // 4.
 *   throw e;
 * } finally {
 *   HttpClientResponseWrapper responseWrapper =
 *     new HttpClientResponseWrapper(requestWrapper, response, error);
 *   handler.handleReceive(responseWrapper, span); // 5.
 * }
 * }</pre>
 *
 * @param <Req> the native http request type of the client.
 * @param <Resp> the native http response type of the client.
 * @since 4.3
 */
// The generic type parameters should always be <HttpClientRequest, HttpClientResponse>. Even if the
// deprecated methods are removed in Brave v6, we should not remove these parameters as that would
// cause a compilation break and lead to revlock.
public final class HttpClientHandler<Req, Resp> extends HttpHandler {
  /** @since 5.7 */
  public static HttpClientHandler<HttpClientRequest, HttpClientResponse> create(
    HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    return new HttpClientHandler<>(httpTracing, null);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #create(HttpTracing)} as it is more portable.
   */
  @Deprecated
  public static <Req, Resp> HttpClientHandler<Req, Resp> create(HttpTracing httpTracing,
    HttpClientAdapter<Req, Resp> adapter) {
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    if (adapter == null) throw new NullPointerException("adapter == null");
    return new HttpClientHandler<>(httpTracing, adapter);
  }

  final Tracer tracer;
  @Deprecated @Nullable final HttpClientAdapter<Req, Resp> adapter; // null when using default types
  final Sampler sampler;
  final SamplerFunction<HttpRequest> httpSampler;
  @Nullable final String serverName;
  final Injector<HttpClientRequest> defaultInjector;

  HttpClientHandler(HttpTracing httpTracing, @Deprecated HttpClientAdapter<Req, Resp> adapter) {
    super(httpTracing.clientRequestParser(), httpTracing.clientResponseParser());
    this.adapter = adapter;
    this.tracer = httpTracing.tracing().tracer();
    this.sampler = httpTracing.tracing().sampler();
    this.httpSampler = httpTracing.clientRequestSampler();
    this.serverName = !"".equals(httpTracing.serverName()) ? httpTracing.serverName() : null;
    // The following allows us to add the method: handleSend(HttpClientRequest request) without
    // duplicating logic from the superclass or deprecated handleReceive methods.
    this.defaultInjector = httpTracing.propagation().injector(HttpClientRequest.SETTER);
  }

  /**
   * Starts the client span after assigning it a name and tags. This {@link
   * Injector#inject(TraceContext, Object) injects} the trace context onto the request before
   * returning.
   *
   * <p>Call this before sending the request on the wire.
   *
   * @see #handleSendWithParent(HttpClientRequest, TraceContext)
   * @see HttpTracing#clientRequestSampler()
   * @see HttpTracing#clientRequestParser()
   * @since 5.7
   */
  public Span handleSend(HttpClientRequest request) {
    if (request == null) throw new NullPointerException("request == null");
    return handleSend(request, tracer.nextSpan(httpSampler, request));
  }

  /**
   * Like {@link #handleSend(HttpClientRequest)}, except explicitly controls the parent of the
   * client span.
   *
   * @param parent the parent of the client span representing this request, or null for a new
   * trace.
   * @see Tracer#nextSpanWithParent(SamplerFunction, Object, TraceContext)
   * @since 5.10
   */
  public Span handleSendWithParent(HttpClientRequest request, @Nullable TraceContext parent) {
    if (request == null) throw new NullPointerException("request == null");
    return handleSend(request, tracer.nextSpanWithParent(httpSampler, request, parent));
  }

  /**
   * Like {@link #handleSend(HttpClientRequest)}, except explicitly controls the span representing
   * the request.
   *
   * @since 5.7
   */
  public Span handleSend(HttpClientRequest request, Span span) {
    if (request == null) throw new NullPointerException("request == null");
    if (span == null) throw new NullPointerException("span == null");
    defaultInjector.inject(span.context(), request);
    return handleStart(request, span);
  }

  @Override void parseRequest(HttpRequest request, Span span) {
    if (serverName != null) span.remoteServiceName(serverName);
    super.parseRequest(request, span);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)}, as this allows more advanced
   * samplers to be used.
   */
  @Deprecated public Span handleSend(Injector<Req> injector, Req request) {
    return handleSend(injector, request, request);
  }

  /**
   * @since 4.3
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)}.
   */
  @Deprecated public <C> Span handleSend(Injector<C> injector, C carrier, Req request) {
    return handleSend(injector, carrier, request, nextSpan(request));
  }

  /**
   * @since 4.4
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest, Span)}.
   */
  @Deprecated public Span handleSend(Injector<Req> injector, Req request, Span span) {
    return handleSend(injector, request, request, span);
  }

  /**
   * @since 4.4
   * @deprecated Since 5.7, use {@link #handleSend(HttpClientRequest)}.
   */
  @Deprecated public <C> Span handleSend(Injector<C> injector, C carrier, Req request, Span span) {
    if (request == null) throw new NullPointerException("carrier == null");
    if (span == null) throw new NullPointerException("span == null");

    injector.inject(span.context(), carrier);
    HttpClientRequest clientRequest;
    if (request instanceof HttpClientRequest) {
      clientRequest = (HttpClientRequest) request;
    } else {
      clientRequest = new HttpClientAdapters.FromRequestAdapter<>(adapter, request);
    }

    return handleStart(clientRequest, span);
  }

  /**
   * @since 4.4
   * @deprecated since 5.8 use {@link Tracer#nextSpan(SamplerFunction, Object)}
   */
  @Deprecated public Span nextSpan(Req request) {
    // nextSpan can be called independently when interceptors control lifecycle directly. In these
    // cases, it is possible to have HttpClientRequest as an argument.
    HttpClientRequest clientRequest;
    if (request instanceof HttpClientRequest) {
      clientRequest = (HttpClientRequest) request;
    } else {
      clientRequest = new FromRequestAdapter<>(adapter, request);
    }
    return tracer.nextSpan(httpSampler, clientRequest);
  }

  /**
   * @since 4.3
   * @deprecated since 5.12 use {@link #handleReceive(HttpClientResponse, Span)}
   */
  @Deprecated
  public void handleReceive(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span == null) throw new NullPointerException("span == null");
    if (response == null && error == null) {
      throw new IllegalArgumentException(
        "Either the response or error parameters may be null, but not both");
    }

    if (response == null) {
      span.error(error).finish();
      return;
    }

    HttpClientResponse clientResponse;
    if (response instanceof HttpClientResponse) {
      clientResponse = (HttpClientResponse) response;
      if (clientResponse.error() == null && error != null) {
        span.error(error);
      }
    } else {
      clientResponse = new FromResponseAdapter<>(adapter, response, error);
    }
    handleFinish(clientResponse, span);
  }

  /**
   * Finishes the client span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are received, and after the span is
   * {@link Tracer.SpanInScope#close() no longer in scope}.
   *
   * <p><em>Note</em>: It is valid to have a {@link HttpClientResponse} that only includes an
   * {@linkplain HttpClientResponse#error() error}. However, it is better to also include the
   * {@linkplain HttpClientResponse#request() request}.
   *
   * @see HttpResponseParser#parse(HttpResponse, TraceContext, SpanCustomizer)
   * @since 5.12
   */
  public void handleReceive(HttpClientResponse response, Span span) {
    handleFinish(response, span);
  }
}

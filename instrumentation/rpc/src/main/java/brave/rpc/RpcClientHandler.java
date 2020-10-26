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
package brave.rpc;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import brave.sampler.SamplerFunction;

/**
 * This standardizes a way to instrument RPC clients, particularly in a way that encourages use of
 * portable customizations via {@link RpcRequestParser} and {@link RpcResponseParser}.
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
 * RpcClientRequestWrapper requestWrapper = new RpcClientRequestWrapper(request);
 * Span span = handler.handleSend(requestWrapper); // 1.
 * ClientResponse response = null;
 * Throwable error = null;
 * try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
 *   return response = invoke(request); // 3.
 * } catch (Throwable e) {
 *   error = e; // 4.
 *   throw e;
 * } finally {
 *   RpcClientResponseWrapper responseWrapper =
 *     new RpcClientResponseWrapper(requestWrapper, response, error);
 *   handler.handleReceive(responseWrapper, span); // 5.
 * }
 * }</pre>
 *
 * @since 5.12
 */
public final class RpcClientHandler extends RpcHandler<RpcClientRequest, RpcClientResponse> {
  /** @since 5.12 */
  public static RpcClientHandler create(RpcTracing rpcTracing) {
    if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
    return new RpcClientHandler(rpcTracing);
  }

  final Tracer tracer;
  final SamplerFunction<RpcRequest> sampler;
  final Injector<RpcClientRequest> injector;

  RpcClientHandler(RpcTracing rpcTracing) {
    super(rpcTracing.clientRequestParser(), rpcTracing.clientResponseParser());
    this.tracer = rpcTracing.tracing().tracer();
    this.injector = rpcTracing.propagation().injector(RpcClientRequest.SETTER);
    this.sampler = rpcTracing.clientSampler();
  }

  /**
   * Starts the client span after assigning it a name and tags. This {@link
   * Injector#inject(TraceContext, Object) injects} the trace context onto the request before
   * returning.
   *
   * <p>Call this before sending the request on the wire.
   *
   * @see #handleSendWithParent(RpcClientRequest, TraceContext)
   * @see RpcTracing#clientSampler()
   * @see RpcTracing#clientRequestParser()
   * @since 5.12
   */
  public Span handleSend(RpcClientRequest request) {
    if (request == null) throw new NullPointerException("request == null");
    return handleSend(request, tracer.nextSpan(sampler, request));
  }

  /**
   * Like {@link #handleSend(RpcClientRequest)}, except explicitly controls the parent of the client
   * span.
   *
   * @param parent the parent of the client span representing this request, or null for a new
   * trace.
   * @see Tracer#nextSpanWithParent(SamplerFunction, Object, TraceContext)
   * @since 5.12
   */
  public Span handleSendWithParent(RpcClientRequest request, @Nullable TraceContext parent) {
    if (request == null) throw new NullPointerException("request == null");
    return handleSend(request, tracer.nextSpanWithParent(sampler, request, parent));
  }

  /**
   * Like {@link #handleSend(RpcClientRequest)}, except explicitly controls the span representing
   * the request.
   *
   * @since 5.12
   */
  public Span handleSend(RpcClientRequest request, Span span) {
    if (request == null) throw new NullPointerException("request == null");
    if (span == null) throw new NullPointerException("span == null");
    injector.inject(span.context(), request);
    return handleStart(request, span);
  }

  /**
   * Finishes the client span after assigning it tags according to the response or error.
   *
   * <p>This is typically called once the response headers are received, and after the span is
   * {@link Tracer.SpanInScope#close() no longer in scope}.
   *
   * <p><em>Note</em>: It is valid to have a {@link RpcClientResponse} that only includes an
   * {@linkplain RpcClientResponse#error() error}. However, it is better to also include the
   * {@linkplain RpcClientResponse#request() request}.
   *
   * @see RpcResponseParser#parse(RpcResponse, TraceContext, SpanCustomizer)
   * @since 5.12
   */
  public void handleReceive(RpcClientResponse response, Span span) {
    handleFinish(response, span);
  }
}

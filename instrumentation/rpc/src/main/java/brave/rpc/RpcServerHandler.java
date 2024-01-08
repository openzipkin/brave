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
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;

/**
 * This standardizes a way to instrument RPC servers, particularly in a way that encourages use of
 * portable customizations via {@link RpcRequestParser} and {@link RpcResponseParser}.
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
 * RpcServerRequestWrapper requestWrapper = new RpcServerRequestWrapper(request);
 * Span span = handler.handleReceive(requestWrapper); // 1.
 * ServerResponse response = null;
 * Throwable error = null;
 * try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
 *   return response = process(request); // 3.
 * } catch (Throwable e) {
 *   error = e; // 4.
 *   throw e;
 * } finally {
 *   RpcServerResponseWrapper responseWrapper =
 *     new RpcServerResponseWrapper(requestWrapper, response, error);
 *   handler.handleSend(responseWrapper, span); // 5.
 * }
 * }</pre>
 *
 * @since 5.12
 */
public final class RpcServerHandler extends RpcHandler<RpcServerRequest, RpcServerResponse> {
  /** @since 5.12 */
  public static RpcServerHandler create(RpcTracing rpcTracing) {
    if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
    return new RpcServerHandler(rpcTracing);
  }

  final Tracer tracer;
  final Extractor<RpcServerRequest> extractor;
  final SamplerFunction<RpcRequest> sampler;

  RpcServerHandler(RpcTracing rpcTracing) {
    super(rpcTracing.serverRequestParser(), rpcTracing.serverResponseParser());
    this.tracer = rpcTracing.tracing().tracer();
    this.extractor = rpcTracing.propagation().extractor(RpcServerRequest.GETTER);
    this.sampler = rpcTracing.serverSampler();
  }

  /**
   * Conditionally joins a span, or starts a new trace, depending on if a trace context was
   * extracted from the request. Tags are added before the span is started.
   *
   * <p>This is typically called before the request is processed by the actual library.
   *
   * @see RpcTracing#serverSampler()
   * @see RpcTracing#serverRequestParser()
   * @since 5.12
   */
  public Span handleReceive(RpcServerRequest request) {
    Span span = nextSpan(extractor.extract(request), request);
    return handleStart(request, span);
  }

  /** Creates a potentially noop span representing this request */
  Span nextSpan(TraceContextOrSamplingFlags extracted, RpcServerRequest request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the RPC sampler made a decision
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
   * <p><em>Note</em>: It is valid to have a {@link RpcServerResponse} that only includes an
   * {@linkplain RpcServerResponse#error() error}. However, it is better to also include the
   * {@linkplain RpcServerResponse#request() request}.
   *
   * @see RpcResponseParser#parse(RpcResponse, TraceContext, SpanCustomizer)
   * @since 5.12
   */
  public void handleSend(RpcServerResponse response, Span span) {
    handleFinish(response, span);
  }
}

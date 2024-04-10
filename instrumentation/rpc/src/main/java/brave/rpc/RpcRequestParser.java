/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;

/**
 * Use this to control the request data recorded for an {@link TraceContext#sampledLocal() sampled
 * RPC client or server span}.
 *
 * <p>Here's an example that adds default tags, and if Apache Dubbo, Java arguments:
 * <pre>{@code
 * rpcTracing = rpcTracingBuilder
 *   .clientRequestParser((req, context, span) -> {
 *      RpcRequestParser.DEFAULT.parse(req, context, span);
 *      if (req instanceof DubboRequest) {
 *        tagArguments(((DubboRequest) req).invocation().getArguments());
 *      }
 *   }).build();
 * }</pre>
 *
 * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as it
 * is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project has
 * a minimum Java language level 6.
 *
 * @see RpcResponseParser
 * @since 5.12
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface RpcRequestParser {
  RpcRequestParser DEFAULT = new Default();

  /**
   * Implement to choose what data from the RPC request are parsed into the span representing it.
   *
   * @see Default
   */
  void parse(RpcRequest request, TraceContext context, SpanCustomizer span);

  /**
   * The default data policy sets the span name to {@code ${rpc.service}/${rpc.method}} or only the
   * method or service. This also tags "rpc.service" and "rpc.method" when present.
   */
  class Default implements RpcRequestParser {
    @Override public void parse(RpcRequest req, TraceContext context, SpanCustomizer span) {
      String service = req.service();
      String method = req.method();
      if (service == null && method == null) return;
      if (service == null) {
        span.tag(RpcTags.METHOD.key(), method);
        span.name(method);
      } else if (method == null) {
        span.tag(RpcTags.SERVICE.key(), service);
        span.name(service);
      } else {
        span.tag(RpcTags.SERVICE.key(), service);
        span.tag(RpcTags.METHOD.key(), method);
        span.name(service + "/" + method);
      }
    }
  }
}

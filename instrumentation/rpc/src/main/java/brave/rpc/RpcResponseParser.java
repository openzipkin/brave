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
import brave.Tags;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

/**
 * Use this to control the response data recorded for an {@link TraceContext#sampledLocal() sampled
 * RPC client or server span}.
 *
 * <p>Here's an example that adds default tags, and if gRPC, the response encoding:
 * <pre>{@code
 * Tag<GrpcResponse> responseEncoding = new Tag<GrpcResponse>("grpc.response_encoding") {
 *   @Override protected String parseValue(GrpcResponse input, TraceContext context) {
 *     return input.headers().get(GrpcUtil.MESSAGE_ENCODING_KEY);
 *   }
 * };
 *
 * RpcResponseParser addResponseEncoding = (res, context, span) -> {
 *   RpcResponseParser.DEFAULT.parse(res, context, span);
 *   if (res instanceof GrpcResponse) responseEncoding.tag((GrpcResponse) res, span);
 * };
 *
 * grpcTracing = GrpcTracing.create(RpcTracing.newBuilder(tracing)
 *     .clientResponseParser(addResponseEncoding);
 *     .serverResponseParser(addResponseEncoding).build());
 * }</pre>
 *
 * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as it
 * is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project has
 * a minimum Java language level 6.
 *
 * @see RpcRequestParser
 * @since 5.12
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface RpcResponseParser {
  RpcResponseParser DEFAULT = new Default();

  /**
   * Implement to choose what data from the RPC response are parsed into the span representing it.
   *
   * <p>Note: This is called after {@link Span#error(Throwable)}, which means any "error" tag set
   * here will overwrite what the {@linkplain Tracing#errorParser() error parser} set.
   *
   * @see Default
   * @since 5.12
   */
  void parse(RpcResponse response, TraceContext context, SpanCustomizer span);

  /**
   * The default data policy sets {@link RpcTags#ERROR_CODE} tag, and also "error", if there is no
   * {@linkplain RpcResponse#error() exception}.
   *
   * <p><em>Note</em>:The exception will be tagged by default in Zipkin, but if you are using a
   * {@link SpanHandler} to another destination, you should process accordingly.
   *
   * @since 5.12
   */
  class Default implements RpcResponseParser {
    @Override public void parse(RpcResponse response, TraceContext context, SpanCustomizer span) {
      String errorCode = response.errorCode();
      if (errorCode != null) {
        span.tag(RpcTags.ERROR_CODE.key(), errorCode);
        if (response.error() == null) {
          span.tag(Tags.ERROR.key(), errorCode);
        }
      }
    }
  }
}

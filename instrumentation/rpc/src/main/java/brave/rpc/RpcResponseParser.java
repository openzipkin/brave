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
package brave.rpc;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tags;
import brave.Tracing;
import brave.handler.FinishedSpanHandler;
import brave.propagation.TraceContext;

/**
 * Use this to control the response data recorded for an {@link TraceContext#sampledLocal() sampled
 * RPC client or server span}.
 *
 * <p>Here's an example that adds the "rpc.error_code" even though "error" contains it.
 * <pre>{@code
 * rpcTracing = rpcTracingBuilder
 *   .clientResponseParser((response, context, span) -> {
 *     RpcResponseParser.DEFAULT.parse(response, context, span);
 *     RpcTags.ERROR_CODE.tag(response, context, span);
 *   }).build();
 * }</pre>
 *
 * @see RpcRequestParser
 * @since 5.12
 */
// @FunctionalInterface: do not add methods as it will break api
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
   * {@link FinishedSpanHandler} to another destination, you should process accordingly.
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

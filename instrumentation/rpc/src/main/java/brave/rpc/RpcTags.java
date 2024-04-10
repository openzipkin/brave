/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.SpanCustomizer;
import brave.Tag;
import brave.propagation.TraceContext;

/**
 * Standard tags used in {@linkplain RpcRequestParser request} and {@linkplain RpcResponseParser
 * response parsers}.
 *
 * @see RpcRequestParser
 * @see RpcResponseParser
 * @since 5.11
 */
public final class RpcTags {
  /**
   * This tags "rpc.method" as the value of {@link RpcRequest#method()}.
   *
   * @see RpcRequest#method()
   * @see RpcRequestParser#parse(RpcRequest, TraceContext, SpanCustomizer)
   * @since 5.11
   */
  public static final Tag<RpcRequest> METHOD = new Tag<RpcRequest>("rpc.method") {
    @Override protected String parseValue(RpcRequest input, TraceContext context) {
      return input.method();
    }
  };

  /**
   * This tags "rpc.service" as the value of {@link RpcRequest#service()}.
   *
   * @see RpcRequest#service()
   * @see RpcRequestParser#parse(RpcRequest, TraceContext, SpanCustomizer)
   * @since 5.11
   */
  public static final Tag<RpcRequest> SERVICE = new Tag<RpcRequest>("rpc.service") {
    @Override protected String parseValue(RpcRequest input, TraceContext context) {
      return input.service();
    }
  };

  /**
   * This tags "rpc.error_code" as the value of {@link RpcResponse#errorCode()}.
   *
   * @see RpcResponse#errorCode()
   * @see RpcResponseParser#parse(RpcResponse, TraceContext, SpanCustomizer)
   * @since 5.11
   */
  public static final Tag<RpcResponse> ERROR_CODE = new Tag<RpcResponse>("rpc.error_code") {
    @Override protected String parseValue(RpcResponse input, TraceContext context) {
      return input.errorCode();
    }
  };

  RpcTags() {
  }
}

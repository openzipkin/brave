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

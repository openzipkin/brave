/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.internal.Nullable;
import brave.rpc.RpcClientResponse;
import brave.rpc.RpcServerResponse;
import brave.rpc.RpcTracing;
import org.apache.dubbo.rpc.Result;

/**
 * Used to access Dubbo specific aspects of a client or server response.
 *
 * <p>Here's an example that adds default tags, and if Dubbo, the Java result:
 * <pre>{@code
 * rpcTracing = rpcTracingBuilder
 *   .clientResponseParser((res, context, span) -> {
 *      RpcResponseParser.DEFAULT.parse(res, context, span);
 *      if (res instanceof DubboResponse) {
 *        DubboResponse dubboResponse = (DubboResponse) res;
 *        if (res.result() != null) {
 *          tagJavaResult(res.result().value());
 *        }
 *      }
 *   }).build();
 * }</pre>
 *
 * <p>Note: Do not implement this type directly. An implementation will be
 * either as {@link RpcClientResponse} or an {@link RpcServerResponse}.
 *
 * @see RpcTracing#clientResponseParser()
 * @see RpcTracing#serverResponseParser()
 * @see DubboResponse
 * @since 5.12
 */
public interface DubboResponse {
  DubboRequest request();

  @Nullable Result result();
}

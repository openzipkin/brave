/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.rpc.RpcClientRequest;
import brave.rpc.RpcServerRequest;
import brave.rpc.RpcTracing;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

/**
 * Used to access Dubbo specific aspects of a client or server request.
 *
 * <p>Here's an example that adds default tags, and if Dubbo, Java arguments:
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
 * <p>Note: Do not implement this type directly. An implementation will be
 * either as {@link RpcClientRequest} or an {@link RpcServerRequest}.
 *
 * @see RpcTracing#clientRequestParser()
 * @see RpcTracing#serverRequestParser()
 * @see DubboResponse
 * @since 5.12
 */
// Note: Unlike Alibaba Dubbo, Apache Dubbo is Java 8+.
// This means we can add default methods later should needs arise.
public interface DubboRequest {
  Invoker<?> invoker();

  Invocation invocation();
}

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
package brave.dubbo;

import brave.rpc.RpcClientRequest;
import brave.rpc.RpcServerRequest;
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
 * @since 5.12
 */
// Note: Unlike Alibaba Dubbo, Apache Dubbo is Java 8+.
// This means we can add default methods later should needs arise.
interface DubboRequest { // TODO: make public after #999
  Invoker<?> invoker();

  Invocation invocation();
}

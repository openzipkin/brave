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
import brave.internal.Nullable;
import java.lang.reflect.Method;

/**
 * Abstract request type used for parsing and sampling of rpc clients and servers.
 *
 * @see RpcClientRequest
 * @see RpcServerRequest
 * @since 5.8
 */
public abstract class RpcRequest {
  /**
   * Returns the underlying rpc request object. Ex. {@code org.apache.rpc.RpcRequest}
   *
   * <p>Note: Some implementations are composed of multiple types, such as a request and a socket
   * address of the client. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code instance
   * of}) instead of presuming a specific type will always be returned.
   *
   * @since 5.8
   */
  public abstract Object unwrap();

  /**
   * The unqualified, case-sensitive method name. Prefer the name defined in IDL to any mapped
   * {@link Method#getName() Java method name}.
   *
   * <p>Examples
   * <pre><ul>
   *   <li>gRPC - full method "grpc.health.v1.Health/Check" returns "Check"</li>
   *   <li>Apache Dubbo - "demo.service.DemoService#sayHello()" command returns "sayHello"</li>
   *   <li>Apache Thrift - full method "scribe.Log" returns "Log"</li>
   * </ul></pre>
   *
   * <p>Note: For IDL based services, such as Protocol Buffers, this may be different than the
   * {@link Method#getName() Java method name}, or in a different case format.
   *
   * @return the RPC method name or null if unreadable.
   */
  @Nullable public abstract String method();

  /**
   * The fully-qualified, case-sensitive service path. Prefer the name defined in IDL to any mapped
   * {@link Package#getName() Java package name}.
   *
   * <p>Examples
   * <pre><ul>
   *   <li>gRPC - full method "grpc.health.v1.Health/Check" returns "grpc.health.v1.Health"</li>
   *   <li>Apache Dubbo - "demo.service.DemoService#sayHello()" command returns "demo.service.DemoService"</li>
   *   <li>Apache Thrift - full method "scribe.Log" returns "scribe"</li>
   * </ul></pre>
   *
   * <p>Note: For IDL based services, such as Protocol Buffers, this may be different than the
   * {@link Package#getName() Java package name}, or in a different case format. Also, this is the
   * definition of the service, not its deployment {@link Span#remoteServiceName(String) service
   * name}.
   *
   * @return the RPC namespace or null if unreadable.
   */
  @Nullable public abstract String service();

  @Override public String toString() {
    Object unwrapped = unwrap();
    // unwrap() returning null is a bug. It could also return this. don't NPE or stack overflow!
    if (unwrapped == null || unwrapped == this) return getClass().getSimpleName();
    return getClass().getSimpleName() + "{" + unwrapped + "}";
  }

  RpcRequest() { // sealed type: only client and server
  }
}

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
   * The unqualified, case-sensitive method name, defined in IDL or the corresponding protocol.
   *
   * <p>Examples
   * <pre><ul>
   *   <li>gRPC - full method "grpc.health.v1.Health/Check" returns "Check"</li>
   *   <li>Apache Thrift - full method "scribe.Log" returns "Log"</li>
   *   <li>Redis - "EXISTS" command returns "EXISTS"</li>
   * </ul></pre>
   *
   * <p>Note: This is not the same as the {@link Method#getName() Java method name}.
   *
   * @return the RPC method name or null if unreadable.
   */
  @Nullable public abstract String method();

  /**
   * The fully-qualified, case-sensitive service path as defined in IDL.
   *
   * <p>Examples
   * <pre><ul>
   *   <li>gRPC - full method "grpc.health.v1.Health/Check" returns "grpc.health.v1.Health"</li>
   *   <li>Apache Thrift - full method "scribe.Log" returns "scribe"</li>
   *   <li>Redis - "EXISTS" command returns null</li>
   * </ul></pre>
   *
   * <p>Note: This is not the deployment {@link Span#remoteServiceName(String) service name}.
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

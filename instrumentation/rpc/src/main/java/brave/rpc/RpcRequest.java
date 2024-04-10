/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Clock;
import brave.Request;
import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.reflect.Method;

/**
 * Abstract request type used for parsing and sampling of RPC clients and servers.
 *
 * @see RpcClientRequest
 * @see RpcServerRequest
 * @since 5.8
 */
public abstract class RpcRequest extends Request {
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
   * @since 5.8
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
   * @since 5.8
   */
  @Nullable public abstract String service();

  /**
   * The timestamp in epoch microseconds of the beginning of this request or zero to take this
   * implicitly from the current clock. Defaults to zero.
   *
   * <p>This is helpful in two scenarios: late parsing and avoiding redundant timestamp overhead.
   * If a server span, this helps reach the "original" beginning of the request, which is always
   * prior to parsing.
   *
   * <p>Note: Overriding has the same problems as using {@link brave.Span#start(long)}. For
   * example, it can result in negative duration if the clock used is allowed to correct backwards.
   * It can also result in misalignments in the trace, unless {@link brave.Tracing.Builder#clock(Clock)}
   * uses the same implementation.
   *
   * @see RpcResponse#finishTimestamp()
   * @see brave.Span#start(long)
   * @see brave.Tracing#clock(TraceContext)
   * @since 5.12
   */
  public long startTimestamp() {
    return 0L;
  }

  /**
   * Override and return true when it is possible to parse the {@link Span#remoteIpAndPort(String,
   * int) remote IP and port} from the {@link #unwrap() delegate}. Defaults to false.
   *
   * @since 5.12
   */
  public boolean parseRemoteIpAndPort(Span span) {
    return false;
  }

  RpcRequest() { // sealed type: only client and server
  }
}

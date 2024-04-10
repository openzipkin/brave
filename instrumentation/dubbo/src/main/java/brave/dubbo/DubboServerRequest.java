/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.Span;
import brave.rpc.RpcServerRequest;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

final class DubboServerRequest extends RpcServerRequest implements DubboRequest {
  final Invoker<?> invoker;
  final Invocation invocation;

  DubboServerRequest(Invoker<?> invoker, Invocation invocation) {
    if (invoker == null) throw new NullPointerException("invoker == null");
    if (invocation == null) throw new NullPointerException("invocation == null");
    this.invoker = invoker;
    this.invocation = invocation;
  }

  @Override public Invoker<?> invoker() {
    return invoker;
  }

  @Override public Invocation invocation() {
    return invocation;
  }

  /** Returns the {@link Invocation}. */
  @Override public Invocation unwrap() {
    return invocation;
  }

  /**
   * Returns the method name of the invocation or the first string arg of an "$invoke" or
   * "$invokeAsync" method.
   */
  @Override public String method() {
    return DubboParser.method(invocation);
  }

  /**
   * Returns the {@link URL#getServiceInterface() service interface} of the invocation.
   */
  @Override public String service() {
    return DubboParser.service(invoker);
  }

  @Override public boolean parseRemoteIpAndPort(Span span) {
    return DubboParser.parseRemoteIpAndPort(span);
  }

  @Override protected String propagationField(String keyName) {
    return invocation.getAttachment(keyName);
  }
}

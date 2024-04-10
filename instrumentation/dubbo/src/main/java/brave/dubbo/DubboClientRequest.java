/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.Span;
import brave.rpc.RpcClientRequest;
import java.util.Map;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

final class DubboClientRequest extends RpcClientRequest implements DubboRequest {
  final Invoker<?> invoker;
  final Invocation invocation;
  final Map<String, String> attachments;

  DubboClientRequest(Invoker<?> invoker, Invocation invocation, Map<String, String> attachments) {
    if (invoker == null) throw new NullPointerException("invoker == null");
    if (invocation == null) throw new NullPointerException("invocation == null");
    this.invoker = invoker;
    this.invocation = invocation;
    this.attachments = attachments;
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
   * Returns the {@link URL#getServiceInterface() service interface} of the invoker.
   */
  @Override public String service() {
    return DubboParser.service(invoker);
  }

  @Override public boolean parseRemoteIpAndPort(Span span) {
    return DubboParser.parseRemoteIpAndPort(span);
  }

  @Override protected void propagationField(String keyName, String value) {
    attachments.put(keyName, value);
  }
}

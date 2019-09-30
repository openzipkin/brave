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
package brave.dubbo.rpc;

import brave.Span;
import brave.rpc.RpcClientRequest;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import java.util.Map;

final class DubboClientRequest extends RpcClientRequest implements DubboRequest {
  final Invoker<?> invoker;
  final Invocation invocation;
  final Map<String, String> attachments;

  DubboClientRequest(Invoker<?> invoker, Invocation invocation, Map<String, String> attachments) {
    if (invoker == null) throw new NullPointerException("invoker == null");
    if (invocation == null) throw new NullPointerException("invocation == null");
    if (attachments == null) throw new NullPointerException("attachments == null");
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
   * Returns the method name of the invocation or the first string arg of an "$invoke" method.
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

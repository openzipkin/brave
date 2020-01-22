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

import brave.internal.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.support.RpcUtils;

final class DubboParser {
  /**
   * Returns the method name of the invocation or the first string arg of an "$invoke" or
   * "$invokeAsync" method.
   *
   * <p>Like {@link RpcUtils#getMethodName(Invocation)}, except without re-reading fields or
   * returning an unhelpful "$invoke" method name.
   */
  static @Nullable String method(Invocation invocation) {
    String methodName = invocation.getMethodName();
    if ("$invoke".equals(methodName) || "$invokeAsync".equals(methodName)) {
      Object[] arguments = invocation.getArguments();
      if (arguments != null && arguments.length > 0 && arguments[0] instanceof String) {
        methodName = (String) arguments[0];
      } else {
        methodName = null;
      }
    }
    return methodName != null && !methodName.isEmpty() ? methodName : null;
  }

  /**
   * Returns the {@link URL#getServiceInterface() service interface} of the invocation.
   *
   * <p>This was chosen as the {@link URL#getServiceName() service name} is deprecated for it.
   */
  static @Nullable String service(Invocation invocation) {
    Invoker<?> invoker = invocation.getInvoker();
    if (invoker == null) return null;
    URL url = invoker.getUrl();
    if (url == null) return null;
    String service = url.getServiceInterface();
    return service != null && !service.isEmpty() ? service : null;
  }
}

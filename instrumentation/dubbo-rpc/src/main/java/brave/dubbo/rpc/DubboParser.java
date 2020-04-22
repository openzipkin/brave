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
import brave.internal.Nullable;
import brave.internal.Platform;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import java.net.InetSocketAddress;

final class DubboParser {
  /**
   * Returns the method name of the invocation or the first string arg of an "$invoke" method.
   *
   * <p>Like {@link RpcUtils#getMethodName(Invocation)}, except without re-reading fields or
   * returning an unhelpful "$invoke" method name.
   */
  static @Nullable String method(Invocation invocation) {
    String methodName = invocation.getMethodName();
    if ("$invoke".equals(methodName)) {
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

  static boolean parseRemoteIpAndPort(Span span) {
    RpcContext rpcContext = RpcContext.getContext();
    InetSocketAddress remoteAddress = rpcContext.getRemoteAddress();
    if (remoteAddress == null) return false;
    return span.remoteIpAndPort(
      Platform.get().getHostString(remoteAddress),
      remoteAddress.getPort()
    );
  }

  /**
   * We decided to not map Dubbo codes to human readable names like {@link
   * RpcException#BIZ_EXCEPTION} even though we defined "rpc.error_code" as a human readable name.
   *
   * <p>The reason was a comparison with HTTP status codes, and the choice was between returning
   * just numbers or reusing "UNKNOWN_EXCEPTION" which is defined in Dubbo for code "0" for any
   * unknown code. Returning numbers was the less bad option as it doesn't conflate code words.
   *
   * <p>Later, we can revert this back to code words, but once this gets into the RPC mapping for
   * Dubbo it will be hard to change.
   */
  @Nullable static String errorCode(Throwable error) {
    if (error instanceof RpcException) {
      return String.valueOf(((RpcException) error).getCode());
    }
    return null;
  }
}

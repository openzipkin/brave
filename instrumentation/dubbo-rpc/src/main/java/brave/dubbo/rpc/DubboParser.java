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
   * Returns the {@link URL#getServiceInterface() service interface} of the invoker.
   *
   * <p>This was chosen as the {@link URL#getServiceName() service name} is deprecated for it.
   */
  @Nullable static String service(Invoker<?> invoker) {
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
   * This library is no-longer being released, so it should not have any maintenance on error codes.
   * The error codes here were defined in 2012.
   */
  @Nullable static String errorCode(Throwable error) {
    if (error instanceof RpcException) {
      int code = ((RpcException) error).getCode();
      switch (code) {
        case RpcException.UNKNOWN_EXCEPTION:
          return "UNKNOWN_EXCEPTION";
        case RpcException.NETWORK_EXCEPTION:
          return "NETWORK_EXCEPTION";
        case RpcException.TIMEOUT_EXCEPTION:
          return "TIMEOUT_EXCEPTION";
        case RpcException.BIZ_EXCEPTION:
          return "BIZ_EXCEPTION";
        case RpcException.FORBIDDEN_EXCEPTION:
          return "FORBIDDEN_EXCEPTION";
        case RpcException.SERIALIZATION_EXCEPTION:
          return "SERIALIZATION_EXCEPTION";
        default:
          return String.valueOf(code);
      }
    }
    return null;
  }
}

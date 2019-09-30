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

import brave.Span;
import brave.internal.Nullable;
import brave.internal.Platform;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.RpcUtils;

final class DubboParser {
  static final Map<Integer, String> ERROR_CODE_NUMBER_TO_NAME = errorCodeNumberToName();

  /**
   * Dubbo adds error codes sometimes. For example, they added two in the last two years. If we did
   * a simple switch/case of each code, like {@link RpcException#BIZ_EXCEPTION}, new code names
   * would not be seen until an upgrade of Brave. That, or we'd have to fall back to a code number
   * until the upgrade of Brave converged.
   *
   * <p>This uses reflection instead, which is unlikely to fail as Dubbo's error codes are public
   * constants.
   */
  static Map<Integer, String> errorCodeNumberToName() {
    Map<Integer, String> result = new LinkedHashMap<>();
    for (Field field : RpcException.class.getDeclaredFields()) {
      if (Modifier.isPublic(field.getModifiers())
          && Modifier.isStatic(field.getModifiers())
          && Modifier.isFinal(field.getModifiers())
          && field.getType() == int.class
      ) {
        try {
          result.put((Integer) field.get(null), field.getName());
        } catch (Exception e) {
          assert false : e.getMessage(); // make all unit tests fail

          // We don't use isAccessible() (deprecated) or canAccess() (Java 9 method)
          // A failure to read a public constant an extreme case, but won't crash anything nor would
          // prevent the normal "error" tag from working.
          Platform.get().log("Error reading error code %s", field, e);
        }
      }
    }
    return result;
  }

  /**
   * Returns the method name of the invocation or the first string arg of an "$invoke" or
   * "$invokeAsync" method.
   *
   * <p>Like {@link RpcUtils#getMethodName(Invocation)}, except without re-reading fields or
   * returning an unhelpful "$invoke" method name.
   */
  @Nullable static String method(Invocation invocation) {
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
   * On occasion, (roughly once a year) Dubbo adds more error code numbers. When this occurs, do not
   * use the symbol name, in the switch statement, as it will affect the minimum version.
   */
  @Nullable static String errorCode(Throwable error) {
    if (error instanceof RpcException) {
      return ERROR_CODE_NUMBER_TO_NAME.get(((RpcException) error).getCode());
    }
    return null;
  }
}

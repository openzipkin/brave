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

import brave.sampler.Matcher;
import brave.sampler.Matchers;

/**
 * Null safe matchers for use in {@link RpcRuleSampler}.
 *
 * @see Matchers
 * @since 5.8
 */
public final class RpcRequestMatchers {

  /**
   * Matcher for case-sensitive RPC method names, such as "Report" or "EXISTS"
   *
   * @see RpcRequest#method()
   * @since 5.8
   */
  public static <Req extends RpcRequest> Matcher<Req> methodEquals(String method) {
    if (method == null) throw new NullPointerException("method == null");
    if (method.isEmpty()) throw new NullPointerException("method is empty");
    return new RpcMethodEquals<>(method);
  }

  static final class RpcMethodEquals<Req extends RpcRequest> implements Matcher<Req> {
    final String method;

    RpcMethodEquals(String method) {
      this.method = method;
    }

    @Override public boolean matches(Req request) {
      return method.equals(request.method());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof RpcMethodEquals)) return false;
      RpcMethodEquals that = (RpcMethodEquals) o;
      return method.equals(that.method);
    }

    @Override public int hashCode() {
      return method.hashCode();
    }

    @Override public String toString() {
      return "RpcMethodEquals(" + method + ")";
    }
  }

  /**
   * Matcher for case-sensitive RPC service names, such as "grpc.health.v1.Health" or "scribe"
   *
   * @see RpcRequest#service()
   * @since 5.8
   */
  public static <Req extends RpcRequest> Matcher<Req> serviceEquals(String service) {
    if (service == null) throw new NullPointerException("service == null");
    if (service.isEmpty()) throw new NullPointerException("service is empty");
    return new RpcServiceEquals<>(service);
  }

  static final class RpcServiceEquals<Req extends RpcRequest> implements Matcher<Req> {
    final String service;

    RpcServiceEquals(String service) {
      this.service = service;
    }

    @Override public boolean matches(Req request) {
      return service.equals(request.service());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof RpcServiceEquals)) return false;
      RpcServiceEquals that = (RpcServiceEquals) o;
      return service.equals(that.service);
    }

    @Override public int hashCode() {
      return service.hashCode();
    }

    @Override public String toString() {
      return "RpcServiceEquals(" + service + ")";
    }
  }
}

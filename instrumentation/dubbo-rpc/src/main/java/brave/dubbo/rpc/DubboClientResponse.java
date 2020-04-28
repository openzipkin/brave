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

import brave.internal.Nullable;
import brave.rpc.RpcClientResponse;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

final class DubboClientResponse extends RpcClientResponse implements DubboResponse {
  final DubboClientRequest request;
  @Nullable final Result result;
  @Nullable final Throwable error;

  DubboClientResponse(
    DubboClientRequest request, @Nullable Result result, @Nullable Throwable error) {
    if (request == null) throw new NullPointerException("request == null");
    this.request = request;
    this.result = result;
    this.error = error;
  }

  @Override public Result result() {
    return result;
  }

  @Override public Result unwrap() {
    return result;
  }

  @Override public DubboClientRequest request() {
    return request;
  }

  @Override public Throwable error() {
    return error;
  }

  /** Returns the string form of the {@link RpcException#getCode()} */
  @Override public String errorCode() {
    return DubboParser.errorCode(error);
  }
}

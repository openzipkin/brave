/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.internal.Nullable;
import brave.rpc.RpcClientResponse;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

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

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import brave.internal.Nullable;
import brave.rpc.RpcServerResponse;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

final class DubboServerResponse extends RpcServerResponse implements DubboResponse {
  final DubboServerRequest request;
  @Nullable final Result result;
  @Nullable final Throwable error;

  DubboServerResponse(
    DubboServerRequest request, @Nullable Result result, @Nullable Throwable error) {
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

  @Override public DubboServerRequest request() {
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

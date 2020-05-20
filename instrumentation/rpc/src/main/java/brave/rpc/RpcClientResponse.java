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
package brave.rpc;

import brave.Span;

/**
 * Marks an interface for use in {@link RpcClientHandler#handleReceive(RpcClientResponse, Span)}.
 * This gives a standard type to consider when parsing an incoming context.
 *
 * @see RpcClientRequest
 * @since 5.10
 */
public abstract class RpcClientResponse extends RpcResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  /**
   * Like {@link RpcRequest#parseRemoteIpAndPort(Span)} for when the client library cannot read
   * socket information before a request is made.
   *
   * <p>To reduce overhead, only implement this when not implementing {@link
   * RpcRequest#parseRemoteIpAndPort(Span)}.
   *
   * @since 5.10
   */
  // This is on the response object because clients often don't know their final IP until after the
  // request started.
  public boolean parseRemoteIpAndPort(Span span) {
    return false;
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

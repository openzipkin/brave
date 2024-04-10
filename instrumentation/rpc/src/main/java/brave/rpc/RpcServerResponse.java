/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Span;

/**
 * Marks an interface for use in {@link RpcServerHandler#handleSend(RpcServerResponse, Span)}. This
 * gives a standard type to consider when parsing an incoming context.
 *
 * @see RpcClientRequest
 * @since 5.10
 */
public abstract class RpcServerResponse extends RpcResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.SERVER;
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Span.Kind;
import brave.baggage.BaggagePropagation;
import brave.propagation.Propagation;
import brave.propagation.Propagation.RemoteSetter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;

/**
 * Marks an interface for use in {@link RpcClientHandler#handleSend(RpcClientRequest)}. This gives a
 * standard type to consider when parsing an outgoing context.
 *
 * @see RpcClientResponse
 * @since 5.8
 */
public abstract class RpcClientRequest extends RpcRequest {
  static final RemoteSetter<RpcClientRequest> SETTER = new RemoteSetter<RpcClientRequest>() {
    @Override public Kind spanKind() {
      return Kind.CLIENT;
    }

    @Override public void put(RpcClientRequest request, String key, String value) {
      request.propagationField(key, value);
    }

    @Override public String toString() {
      return "RpcClientRequest::propagationField";
    }
  };

  @Override public final Kind spanKind() {
    return Kind.CLIENT;
  }

  /**
   * Sets a propagation field with the indicated name. {@code null} values are unsupported.
   *
   * <h3>Notes</h3>
   * <p>This is only used when {@link Injector#inject(TraceContext, Object) injecting} a trace
   * context as internally implemented by {@link RpcClientHandler}. Calls during sampling or parsing
   * are invalid and may be ignored by instrumentation.
   *
   * <p>Header based requests will use headers, but this could set RPC
   * envelopes or even binary data.
   *
   * @param keyName key used for {@link Setter#put}
   * @param value value used for {@link Setter#put}
   * @see #SETTER
   * @see Propagation#keys()
   * @see BaggagePropagation#allKeyNames(Propagation)
   * @since 5.12
   */
  protected void propagationField(String keyName, String value) {
  }
}

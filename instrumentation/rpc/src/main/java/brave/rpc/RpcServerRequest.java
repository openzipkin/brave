/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Span;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.TraceContext;

/**
 * Marks an interface for use in {@link RpcServerHandler#handleReceive(RpcServerRequest)}. This
 * gives a standard type to consider when parsing an incoming context.
 *
 * @see RpcServerResponse
 * @since 5.8
 */
public abstract class RpcServerRequest extends RpcRequest {
  static final RemoteGetter<RpcServerRequest> GETTER = new RemoteGetter<RpcServerRequest>() {
    @Override public Span.Kind spanKind() {
      return Span.Kind.SERVER;
    }

    @Override public String get(RpcServerRequest request, String key) {
      return request.propagationField(key);
    }

    @Override public String toString() {
      return "RpcServerRequest::propagationField";
    }
  };

  @Override public final Span.Kind spanKind() {
    return Span.Kind.SERVER;
  }

  /**
   * Returns one value corresponding to the specified {@link Getter#get propagation field}, or
   * null.
   *
   * <p><em>Note</em>: Header based requests will use headers, but this could be read from RPC
   * envelopes or even binary data.
   * <h3>Notes</h3>
   * <p>This is only used when {@link TraceContext.Injector#inject(TraceContext, Object) injecting}
   * a trace context as internally implemented by {@link RpcClientHandler}. Calls during sampling or
   * parsing are invalid and may be ignored by instrumentation.
   *
   * <p>Header based requests will use headers, but this could set RPC
   * envelopes or even binary data.
   *
   * @param keyName key used for {@link Getter#get(Object, Object)}
   * @see #GETTER
   * @see Propagation#keys()
   * @see BaggagePropagation#allKeyNames(Propagation)
   * @since 5.12
   */
  @Nullable protected String propagationField(String keyName) {
    return null;
  }
}

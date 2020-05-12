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

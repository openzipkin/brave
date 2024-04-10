/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span.Kind;
import brave.propagation.Propagation.RemoteSetter;
import brave.propagation.TraceContext;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleSend(HttpClientRequest)}. This gives
 * a standard type to consider when parsing an outgoing context.
 *
 * @see HttpClientResponse
 * @since 5.7
 */
public abstract class HttpClientRequest extends HttpRequest {
  static final RemoteSetter<HttpClientRequest> SETTER = new RemoteSetter<HttpClientRequest>() {
    @Override public Kind spanKind() {
      return Kind.CLIENT;
    }

    @Override public void put(HttpClientRequest request, String key, String value) {
      request.header(key, value);
    }

    @Override public String toString() {
      return "HttpClientRequest::header";
    }
  };

  @Override public final Kind spanKind() {
    return Kind.CLIENT;
  }

  /**
   * Sets a request header with the indicated name. {@code null} values are unsupported.
   *
   * <p>This is only used when {@link TraceContext.Injector#inject(TraceContext, Object) injecting}
   * a trace context as internally implemented by {@link HttpClientHandler}. Calls during sampling or
   * parsing are invalid and may be ignored by instrumentation.
   *
   * @see #SETTER
   * @since 5.7
   */
  public abstract void header(String name, String value);
}

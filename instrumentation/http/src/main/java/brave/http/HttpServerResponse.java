/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import brave.internal.Nullable;

/**
 * Marks an interface for use in {@link HttpServerHandler#handleSend(Object, Throwable, Span)}. This
 * gives a standard type to consider when parsing an outgoing context.
 *
 * @see HttpServerRequest
 * @since 5.7
 */
public abstract class HttpServerResponse extends HttpResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.SERVER;
  }

  /** {@inheritDoc} */
  @Override @Nullable public HttpServerRequest request() {
    return null;
  }

  @Override public Throwable error() {
    return null; // error() was added in v5.10, but this type was added in v5.7
  }
}

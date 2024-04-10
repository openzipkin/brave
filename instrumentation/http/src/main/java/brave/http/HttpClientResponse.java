/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import brave.internal.Nullable;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleReceive(HttpClientResponse, Span)}.
 * This gives a standard type to consider when parsing an incoming context.
 *
 * @see HttpClientRequest
 * @since 5.7
 */
public abstract class HttpClientResponse extends HttpResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  /** {@inheritDoc} */
  @Override @Nullable public HttpClientRequest request() {
    return null;
  }

  @Override public Throwable error() {
    return null; // error() was added in v5.10, but this type was added in v5.7
  }
}

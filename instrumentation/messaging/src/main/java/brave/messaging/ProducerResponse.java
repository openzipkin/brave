/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Span;

/**
 * @see ProducerRequest
 * @since 5.13
 */
public abstract class ProducerResponse extends MessagingResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.PRODUCER;
  }
}

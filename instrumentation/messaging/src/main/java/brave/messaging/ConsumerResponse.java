/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Span;

/**
 * @see ConsumerRequest
 * @since 5.13
 */
public abstract class ConsumerResponse extends MessagingResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.CONSUMER;
  }
}

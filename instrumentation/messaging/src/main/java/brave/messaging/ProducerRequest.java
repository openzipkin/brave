/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Span;

/**
 * Marks an interface for use in injection and {@link MessagingRuleSampler}. This gives a standard
 * type to consider when parsing an outgoing context.
 *
 * @since 5.9
 */
public abstract class ProducerRequest extends MessagingRequest {
  @Override public Span.Kind spanKind() {
    return Span.Kind.PRODUCER;
  }
}

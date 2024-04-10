/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Span;

import static brave.Span.Kind.CONSUMER;

/**
 * Marks an interface for use in extraction and {@link MessagingRuleSampler}. This gives a standard
 * type to consider when parsing an incoming context.
 *
 * @since 5.9
 */
public abstract class ConsumerRequest extends MessagingRequest {
  @Override public Span.Kind spanKind() {
    return CONSUMER;
  }
}

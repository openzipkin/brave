/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Response;
import brave.internal.Nullable;

/**
 * Abstract response type used for parsing and sampling of messaging clients and servers.
 *
 * @see ProducerResponse
 * @see ConsumerResponse
 * @since 5.13
 */
public abstract class MessagingResponse extends Response {
  /**
   * Information about the request that initiated this messaging response or {@code null} if
   * unknown.
   *
   * @since 5.13
   */
  @Override @Nullable public MessagingRequest request() {
    return null;
  }
}

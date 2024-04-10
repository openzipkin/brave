/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Request;
import brave.internal.Nullable;

/**
 * Abstract request type used for parsing and sampling of messaging producers and consumers.
 *
 * @see ProducerRequest
 * @see ConsumerRequest
 * @since 5.9
 */
public abstract class MessagingRequest extends Request {
  /**
   * The unqualified, case-sensitive semantic message operation name. The currently defined names
   * are "send" and "receive".
   *
   * <p>Examples:
   * <pre><ul>
   *   <li>Amazon SQS - {@code AmazonSQS.sendMessageBatch()} is a "send" operation</li>
   *   <li>JMS - {@code MessageProducer.send()} is a "send" operation</li>
   *   <li>Kafka - {@code Consumer.poll()} is a "receive" operation</li>
   *   <li>RabbitMQ - {@code Consumer.handleDelivery()} is a "receive" operation</li>
   * </ul></pre>
   *
   * <p>Note: There is no constant set of operations, yet. Even when there is a constant set, there
   * may be operations such as "browse" or "purge" which aren't defined. Once implementation
   * matures, a constant file will be defined, with potentially more names.
   *
   * @return the messaging operation or null if unreadable.
   * @since 5.9
   */
  @Nullable public abstract String operation();

  /**
   * Type of channel, e.g. "queue" or "topic". {@code null} if unreadable.
   *
   * <p>Conventionally associated with the tag "messaging.channel_kind"
   *
   * @see #channelName()
   * @since 5.9
   */
  // Naming matches conventions for Span
  @Nullable public abstract String channelKind();

  /**
   * Messaging channel name, e.g. "hooks" or "complaints". {@code null} if unreadable.
   *
   * <p>Conventionally associated with the tag "messaging.channel_name"
   *
   * @see #channelKind()
   * @since 5.9
   */
  @Nullable public abstract String channelName();

  /**
   * The possibly system generated value that identifies this message across one or more links. Ex
   * "ID:10.77.42.209-4280-1477454185311-1:1:1391:1:1". Return {@code null} if the ID was unreadable
   * or the transport has no canonical message ID.
   *
   * <p>This is conventionally associated with the tag "messaging.id"
   *
   * <h3>Notes</h3>
   * Most commonly, a consumer can read this value at request time, but a producer cannot until
   * response time. Sometimes the values seen by either side are different.
   *
   * <p>When there's no field named "message ID", there could be an offset or sequence ID.
   *
   * @since 5.13
   */
  @Nullable public String messageId() {
    return null; // as added late
  }

  MessagingRequest() { // sealed type: only producer and consumer
  }
}

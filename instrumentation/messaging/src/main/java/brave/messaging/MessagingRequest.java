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

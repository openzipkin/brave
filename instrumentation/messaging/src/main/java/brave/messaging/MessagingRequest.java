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
   * <p>Conventionally associated with the key "messaging.channel_kind"
   *
   * @see #channelName()
   * @since 5.9
   */
  // Naming matches conventions for Span
  @Nullable public abstract String channelKind();

  /**
   * Messaging channel name, e.g. "hooks" or "complaints". {@code null} if unreadable.
   *
   * <p>Conventionally associated with the key "messaging.channel_name"
   *
   * @see #channelKind()
   * @since 5.9
   */
  @Nullable public abstract String channelName();

  /**
   * The possibly system generated value that uniquely identifies this message. Return {@code null}
   * if the ID was unreadable or the transport has no canonical message ID format.
   *
   * <p>Examples:
   * <pre><ul>
   *   <li>Amazon SQS - "MessageId" response field. ex "5fea7756-0ea4-451a-a703-a558b933e274"</li>
   *   <li>JMS - "JMSMessageID" header set by the implementation. Ex "ID:10.77.42.209-4280-1477454185311-1:1:1391:1:1"</li>
   *   <li>RabbitMQ - "message-id" property set by the user (not the client library) (max 256 char). ex "5fea7756-0ea4-451a-a703-a558b933e274"</li>
   *   <li>RocketMQ - "MessageId" response field in HEX(ip|port|offset) format. ex "24084004018081003FAA1DDE2B3F898A00002A9F0000000000000CA0"</li>
   * </ul></pre>
   *
   * <p>Conventionally associated with the key "messaging.id"
   *
   * <h3>Notes</h3>
   * The value is set differently per backend. Most commonly, a consumer can read this value at
   * request time, but a producer cannot until response time. For example, a Kafka JMS message ID is
   * derived from partition and offset fields. The offset is only visible after the message is sent.
   *
   * <p>Even though this field is often used for duplicate detection, it is not guaranteed to be
   * immutable on all messaging backends.
   *
   * @since 5.13
   */
  @Nullable public String messageId() {
    return null; // as added late
  }

  MessagingRequest() { // sealed type: only producer and consumer
  }
}

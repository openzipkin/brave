/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.internal.Nullable;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import static brave.internal.Throwables.propagateIfFatal;
import static brave.jakarta.jms.JmsTracing.log;

// Utility class until full parsing support is added
final class MessageParser {

  @Nullable static Destination destination(Message message) {
    try {
      return message.getJMSDestination();
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting destination of message {0}", message, null);
    }
    return null;
  }

  @Nullable  static String messageId(Message message) {
    try {
      return message.getJMSMessageID();
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting getJMSMessageID of message {0}", message, null);
    }
    return null;
  }

  @Nullable static String channelKind(@Nullable Destination destination) {
    if (destination == null) return null;
    return isQueue(destination) ? "queue" : "topic";
  }

  /**
   * Handles special case of a destination being both a Queue and a Topic by checking if the
   * {@linkplain Queue#getQueueName() queue name} is readable and not {@code null}.
   */
  static boolean isQueue(@Nullable Destination destination) {
    boolean isQueue = destination instanceof Queue;
    boolean isTopic = destination instanceof Topic;
    if (isQueue && isTopic) {
      try {
        // The JMS 1.1 specification does not define the result of queue name or topic name,
        // including whether it is null or not. In practice, at least one implementation of both
        // Queue and Topic conditionally returns non-null on getQueueName() or getTopicName()
        // at runtime to indicate if it is a Queue or Topic. See issue #1098.
        isQueue = ((Queue) destination).getQueueName() != null;
      } catch (Throwable t) {
        propagateIfFatal(t);
        log(t, "error getting destination name from {0}", destination, null);
      }
    }
    return isQueue;
  }

  /**
   * Similar to other properties, {@code null} should be expected even if it seems unintuitive.
   *
   * <p>The JMS 1.1 specification 4.2.1 suggests destination details are provider specific.
   * Further, JavaDoc on {@link Queue#getQueueName()} and {@link Topic#getTopicName()} say "Clients
   * that depend upon the name are not portable." Next, such operations can raise {@link
   * JMSException} messages which this code can coerce to null. Finally, destinations are not
   * constrained to implement only one of {@link Queue} or {@link Destination}. This implies one
   * could return null while the other doesn't, such as was the case in issue #1098.
   */
  @Nullable static String channelName(@Nullable Destination destination) {
    if (destination == null) return null;
    boolean isQueue = isQueue(destination);
    try {
      if (isQueue) {
        return ((Queue) destination).getQueueName();
      } else {
        return ((Topic) destination).getTopicName();
      }
    } catch (Throwable t) {
      propagateIfFatal(t);
      log(t, "error getting destination name from {0}", destination, null);
    }
    return null;
  }
}

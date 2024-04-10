/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.rabbit;

import brave.Span.Kind;
import brave.messaging.ProducerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

// intentionally not yet public until we add tag parsing functionality
final class MessageProducerRequest extends ProducerRequest {
  static final RemoteGetter<MessageProducerRequest> GETTER =
      new RemoteGetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public String get(MessageProducerRequest request, String name) {
          return MessageHeaders.getHeaderIfString(request.delegate, name);
        }

        @Override public String toString() {
          return "MessageProperties::getHeader";
        }
      };

  static final RemoteSetter<MessageProducerRequest> SETTER =
      new RemoteSetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public void put(MessageProducerRequest request, String name, String value) {
          MessageHeaders.setHeader(request.delegate, name, value);
        }

        @Override public String toString() {
          return "MessageProperties::setHeader";
        }
      };

  final Message delegate;

  MessageProducerRequest(Message delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public Kind spanKind() {
    return Kind.PRODUCER;
  }

  @Override public Object unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "send";
  }

  @Override public String channelKind() {
    return "queue";
  }

  @Override public String channelName() {
    MessageProperties properties = delegate.getMessageProperties();
    return properties != null ? properties.getConsumerQueue() : null;
  }

  @Override public String messageId() {
    MessageProperties properties = delegate.getMessageProperties();
    return properties != null ? properties.getMessageId() : null;
  }
}

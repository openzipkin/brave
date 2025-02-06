/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Span.Kind;
import brave.messaging.ProducerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;

// intentionally not yet public until we add tag parsing functionality
final class MessageProducerRequest extends ProducerRequest {
  static final RemoteGetter<MessageProducerRequest> GETTER =
      new RemoteGetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public String get(MessageProducerRequest request, String name) {
          return request.delegate.getUserProperty(name);
        }

        @Override public String toString() {
          return "Message::getUserProperty";
        }
      };

  static final RemoteSetter<MessageProducerRequest> SETTER =
      new RemoteSetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public void put(MessageProducerRequest request, String name, String value) {
          request.delegate.putUserProperty(name, value);
        }

        @Override public String toString() {
          return "Message::putUserProperty";
        }
      };

  final Message delegate;

  MessageProducerRequest(Message delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public Message unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "send";
  }

  @Override public String channelKind() {
    return "topic";
  }

  @Override public String channelName() {
    return delegate.getTopic();
  }

  @Override public String messageId() {
    return delegate.getProperty(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX);
  }
}

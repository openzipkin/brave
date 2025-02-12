/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Span;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.rocketmq.common.message.MessageExt;

// intentionally not yet public until we add tag parsing functionality
final class MessageConsumerRequest extends ConsumerRequest {
  static final RemoteGetter<MessageConsumerRequest> GETTER =
    new RemoteGetter<MessageConsumerRequest>() {
      @Override public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override public String get(MessageConsumerRequest request, String name) {
        return request.delegate.getUserProperty(name);
      }

      @Override public String toString() {
        return "MessageExt::getUserProperty";
      }
    };

  static final RemoteSetter<MessageConsumerRequest> SETTER =
    new RemoteSetter<MessageConsumerRequest>() {
      @Override public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override public void put(MessageConsumerRequest request, String name, String value) {
        request.delegate.putUserProperty(name, value);
      }

      @Override public String toString() {
        return "MessageExt::putUserProperty";
      }
    };

  final MessageExt delegate;

  MessageConsumerRequest(MessageExt delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public MessageExt unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "receive";
  }

  @Override public String channelKind() {
    return "topic";
  }

  @Override public String channelName() {
    return delegate.getTopic();
  }

  @Override public String messageId() {
    return delegate.getMsgId();
  }
}

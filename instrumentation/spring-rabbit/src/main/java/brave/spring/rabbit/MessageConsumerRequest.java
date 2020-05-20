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
package brave.spring.rabbit;

import brave.Span.Kind;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

// intentionally not yet public until we add tag parsing functionality
final class MessageConsumerRequest extends ConsumerRequest {
  static final RemoteGetter<MessageConsumerRequest> GETTER =
      new RemoteGetter<MessageConsumerRequest>() {
        @Override public Kind spanKind() {
          return Kind.CONSUMER;
        }

        @Override public String get(MessageConsumerRequest request, String name) {
          return MessageHeaders.getHeaderIfString(request.delegate, name);
        }

        @Override public String toString() {
          return "MessageProperties::getHeader";
        }
      };

  static final RemoteSetter<MessageConsumerRequest> SETTER =
      new RemoteSetter<MessageConsumerRequest>() {
        @Override public Kind spanKind() {
          return Kind.CONSUMER;
        }

        @Override public void put(MessageConsumerRequest request, String name, String value) {
          MessageHeaders.setHeader(request.delegate, name, value);
        }

        @Override public String toString() {
          return "MessageProperties::setHeader";
        }
      };

  final Message delegate;

  MessageConsumerRequest(Message delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public Kind spanKind() {
    return Kind.CONSUMER;
  }

  @Override public Object unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "receive";
  }

  @Override public String channelKind() {
    return "queue";
  }

  @Override public String channelName() {
    MessageProperties properties = delegate.getMessageProperties();
    return properties != null ? properties.getConsumerQueue() : null;
  }
}

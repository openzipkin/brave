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
}

/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.jms;

import brave.Span;
import brave.internal.Nullable;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import javax.jms.Destination;
import javax.jms.Message;

// intentionally not yet public until we add tag parsing functionality
final class MessageConsumerRequest extends ConsumerRequest {
  static final Getter<MessageConsumerRequest, String> GETTER =
    new Getter<MessageConsumerRequest, String>() {
      @Override public String get(MessageConsumerRequest request, String name) {
        return request.getProperty(name);
      }

      @Override public String toString() {
        return "MessageConsumerRequest::getProperty";
      }
    };

  static final Setter<MessageConsumerRequest, String> SETTER =
    new Setter<MessageConsumerRequest, String>() {
      @Override public void put(MessageConsumerRequest request, String name, String value) {
        request.setProperty(name, value);
      }

      @Override public String toString() {
        return "MessageConsumerRequest::setProperty";
      }
    };

  final Message delegate;
  @Nullable final Destination destination;

  MessageConsumerRequest(Message delegate, @Nullable Destination destination) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.destination = destination;
  }

  @Override public Span.Kind spanKind() {
    return Span.Kind.CONSUMER;
  }

  @Override public Object unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "receive";
  }

  @Override public String channelKind() {
    return MessageParser.channelKind(destination);
  }

  @Override public String channelName() {
    return MessageParser.channelName(destination);
  }

  @Nullable String getProperty(String name) {
    return MessagePropagation.GETTER.get(delegate, name);
  }

  void setProperty(String name, String value) {
    MessagePropagation.SETTER.put(delegate, name, value);
  }
}

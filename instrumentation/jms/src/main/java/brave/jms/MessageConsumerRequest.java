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
package brave.jms;

import brave.Span.Kind;
import brave.internal.Nullable;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import javax.jms.Destination;
import javax.jms.Message;

import static brave.jms.MessageProperties.getPropertyIfString;
import static brave.jms.MessageProperties.setStringProperty;

// intentionally not yet public until we add tag parsing functionality
final class MessageConsumerRequest extends ConsumerRequest {
  static final RemoteGetter<MessageConsumerRequest> GETTER =
    new RemoteGetter<MessageConsumerRequest>() {
      @Override public Kind spanKind() {
        return Kind.CONSUMER;
      }

      @Override public String get(MessageConsumerRequest request, String name) {
        return getPropertyIfString(request.delegate, name);
      }

      @Override public String toString() {
        return "Message::getStringProperty";
      }
    };

  static final RemoteSetter<MessageConsumerRequest> SETTER =
    new RemoteSetter<MessageConsumerRequest>() {
      @Override public Kind spanKind() {
        return Kind.CONSUMER;
      }

      @Override public void put(MessageConsumerRequest request, String name, String value) {
        setStringProperty(request.delegate, name, value);
      }

      @Override public String toString() {
        return "Message::setStringProperty";
      }
    };

  final Message delegate;
  @Nullable final Destination destination;

  MessageConsumerRequest(Message delegate, @Nullable Destination destination) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.destination = destination;
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
    return MessageParser.channelKind(destination);
  }

  @Override public String channelName() {
    return MessageParser.channelName(destination);
  }
}

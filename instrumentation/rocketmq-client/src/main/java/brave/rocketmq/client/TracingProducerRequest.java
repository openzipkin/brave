/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.rocketmq.client;

import brave.Span.Kind;
import brave.messaging.ProducerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.rocketmq.common.message.Message;

final class TracingProducerRequest extends ProducerRequest {
  static final RemoteGetter<TracingProducerRequest> GETTER =
    new RemoteGetter<TracingProducerRequest>() {
      @Override public Kind spanKind() {
        return Kind.PRODUCER;
      }

      @Override public String get(TracingProducerRequest request, String name) {
        return request.delegate.getUserProperty(name);
      }

      @Override public String toString() {
        return "Message::getUserProperty";
      }
    };

  static final RemoteSetter<TracingProducerRequest> SETTER =
    new RemoteSetter<TracingProducerRequest>() {
      @Override public Kind spanKind() {
        return Kind.PRODUCER;
      }

      @Override public void put(TracingProducerRequest request, String name, String value) {
        request.delegate.putUserProperty(name, value);
      }

      @Override public String toString() {
        return "Message::putUserProperty";
      }
    };

  final Message delegate;

  TracingProducerRequest(Message delegate) {
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
    return null;
  }
}

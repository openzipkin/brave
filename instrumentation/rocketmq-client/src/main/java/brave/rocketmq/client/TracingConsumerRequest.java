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

import brave.Span;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.rocketmq.common.message.MessageExt;

// intentionally not yet public until we add tag parsing functionality
final class TracingConsumerRequest extends ConsumerRequest {
  static final RemoteGetter<TracingConsumerRequest> GETTER =
    new RemoteGetter<TracingConsumerRequest>() {
      @Override public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override public String get(TracingConsumerRequest request, String name) {
        return request.delegate.getUserProperty(name);
      }

      @Override public String toString() {
        return "MessageExt::getUserProperty";
      }
    };

  static final RemoteSetter<TracingConsumerRequest> SETTER =
    new RemoteSetter<TracingConsumerRequest>() {
      @Override public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override public void put(TracingConsumerRequest request, String name, String value) {
        request.delegate.putUserProperty(name, value);
      }

      @Override public String toString() {
        return "MessageExt::putUserProperty";
      }
    };

  final MessageExt delegate;

  TracingConsumerRequest(MessageExt delegate) {
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

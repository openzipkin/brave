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
package brave.kafka.clients;

import brave.Span.Kind;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import static brave.kafka.clients.KafkaHeaders.lastStringHeader;

// intentionally not yet public until we add tag parsing functionality
final class KafkaConsumerRequest extends ConsumerRequest {
  static final RemoteGetter<KafkaConsumerRequest> GETTER =
      new RemoteGetter<KafkaConsumerRequest>() {
        @Override public Kind spanKind() {
          return Kind.CONSUMER;
        }

        @Override public String get(KafkaConsumerRequest request, String name) {
          return lastStringHeader(request.delegate.headers(), name);
        }

        @Override public String toString() {
          return "Headers::lastHeader";
        }
      };

  static final RemoteSetter<KafkaConsumerRequest> SETTER =
      new RemoteSetter<KafkaConsumerRequest>() {
        @Override public Kind spanKind() {
          return Kind.CONSUMER;
        }

        @Override public void put(KafkaConsumerRequest request, String name, String value) {
          KafkaHeaders.replaceHeader(request.delegate.headers(), name, value);
        }

        @Override public String toString() {
          return "Headers::replace";
        }
      };

  final ConsumerRecord<?, ?> delegate;

  KafkaConsumerRequest(ConsumerRecord<?, ?> delegate) {
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
    return "topic";
  }

  @Override public String channelName() {
    return delegate.topic();
  }
}

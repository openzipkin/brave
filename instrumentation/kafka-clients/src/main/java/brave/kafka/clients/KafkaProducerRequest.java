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
import brave.messaging.ProducerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import org.apache.kafka.clients.producer.ProducerRecord;

import static brave.kafka.clients.KafkaHeaders.lastStringHeader;

// intentionally not yet public until we add tag parsing functionality
final class KafkaProducerRequest extends ProducerRequest {
  static final RemoteGetter<KafkaProducerRequest> GETTER =
      new RemoteGetter<KafkaProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public String get(KafkaProducerRequest request, String name) {
          return lastStringHeader(request.delegate.headers(), name);
        }

        @Override public String toString() {
          return "Headers::lastHeader";
        }
      };

  static final RemoteSetter<KafkaProducerRequest> SETTER =
      new RemoteSetter<KafkaProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public void put(KafkaProducerRequest request, String name, String value) {
          KafkaHeaders.replaceHeader(request.delegate.headers(), name, value);
        }

        @Override public String toString() {
          return "Headers::replace";
        }
      };

  final ProducerRecord<?, ?> delegate;

  KafkaProducerRequest(ProducerRecord<?, ?> delegate) {
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
    return "topic";
  }

  @Override public String channelName() {
    return delegate.topic();
  }
}

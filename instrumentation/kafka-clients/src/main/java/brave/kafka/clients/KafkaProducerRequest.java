/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.Span.Kind;
import brave.internal.Nullable;
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

  @Nullable @Override public String messageId() {
    // Kafka has no message ID, but an offset/sequence field will soon be a standard field
    return null;
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

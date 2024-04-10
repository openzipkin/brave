/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.Span.Kind;
import brave.internal.Nullable;
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

  @Nullable @Override public String messageId() {
    // Kafka has no message ID, but an offset/sequence field will soon be a standard field
    return null;
  }
}

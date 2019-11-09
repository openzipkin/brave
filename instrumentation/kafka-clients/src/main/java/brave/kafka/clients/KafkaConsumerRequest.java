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
package brave.kafka.clients;

import brave.Span;
import brave.internal.Nullable;
import brave.messaging.ConsumerRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import static brave.kafka.clients.KafkaPropagation.GETTER;
import static brave.kafka.clients.KafkaPropagation.SETTER;

// intentionally not yet public until we add tag parsing functionality
final class KafkaConsumerRequest extends ConsumerRequest {
  final ConsumerRecord<?, ?> delegate;

  KafkaConsumerRequest(ConsumerRecord<?, ?> delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
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
    return "topic";
  }

  @Override public String channelName() {
    return delegate.topic();
  }

  @Nullable String getHeader(String key) {
    return GETTER.get(delegate.headers(), key);
  }

  void setHeader(String key, String value) {
    SETTER.put(delegate.headers(), key, value);
  }
}

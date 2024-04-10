/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.ProcessingContext;

final class KafkaStreamsPropagation {
  /**
   * Used by {@link KafkaStreamsTracing#nextSpan(ProcessingContext, Headers)} to extract a trace
   * context from a prior stage.
   */
  static final Getter<Headers, String> GETTER = new Getter<Headers, String>() {
    @Override public String get(Headers headers, String key) {
      return KafkaHeaders.lastStringHeader(headers, key);
    }

    @Override public String toString() {
      return "Headers::lastHeader";
    }
  };

  /** Used to inject the trace context between stages. */
  static final Setter<Headers, String> SETTER = new Setter<Headers, String>() {
    @Override public void put(Headers headers, String key, String value) {
      KafkaHeaders.replaceHeader(headers, key, value);
    }

    @Override public String toString() {
      return "Headers::replaceHeader";
    }
  };

  KafkaStreamsPropagation() {
  }
}

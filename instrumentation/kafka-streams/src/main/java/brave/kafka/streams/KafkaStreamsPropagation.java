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
package brave.kafka.streams;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.ProcessorContext;

final class KafkaStreamsPropagation {
  /**
   * Used by {@link KafkaStreamsTracing#nextSpan(ProcessorContext)} to extract a trace context from
   * a prior stage.
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

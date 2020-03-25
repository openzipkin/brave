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

import brave.Span;
import brave.propagation.B3SingleFormat;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class KafkaTracingTest extends ITKafka {
  @Test public void nextSpan_prefers_b3_header() {
    TraceContext incoming = newTraceContext(SamplingFlags.SAMPLED);
    fakeRecord.headers().add("b3", B3SingleFormat.writeB3SingleFormatAsBytes(incoming));

    Span child;
    try (Scope ws = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaTracing.nextSpan(fakeRecord);
    }
    assertChildOf(child.context(), incoming);
  }

  @Test public void nextSpan_uses_current_context() {
    Span child;
    try (Scope ws = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaTracing.nextSpan(fakeRecord);
    }
    assertChildOf(child.context(), parent);
  }

  @Test public void nextSpan_should_create_span_if_no_headers() {
    assertThat(kafkaTracing.nextSpan(fakeRecord)).isNotNull();
  }

  @Test public void nextSpan_should_create_span_with_extra_keys() {
    addB3MultiHeaders(fakeRecord);
    fakeRecord.headers().add("user-id", "user1".getBytes());

    Span span = kafkaTracing.nextSpan(fakeRecord);
    assertThat(ExtraFieldPropagation.get(span.context(), "user-id")).contains("user1");
  }

  @Test public void nextSpan_should_tag_topic_and_key_when_no_incoming_context() {
    kafkaTracing.nextSpan(fakeRecord).start().finish();

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC), entry("kafka.key", TEST_KEY));
  }

  @Test public void nextSpan_shouldnt_tag_null_key() {
    fakeRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1, null, TEST_VALUE);

    kafkaTracing.nextSpan(fakeRecord).start().finish();

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  @Test public void nextSpan_shouldnt_tag_binary_key() {
    ConsumerRecord<byte[], String> record =
      new ConsumerRecord<>(TEST_TOPIC, 0, 1, new byte[1], TEST_VALUE);

    kafkaTracing.nextSpan(record).start().finish();

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  /**
   * We assume topic and key are already tagged by the producer span. However, we can change this
   * policy now, or later when dynamic policy is added to KafkaTracing
   */
  @Test public void nextSpan_shouldnt_tag_topic_and_key_when_incoming_context() {
    addB3MultiHeaders(fakeRecord);
    kafkaTracing.nextSpan(fakeRecord).start().finish();

    assertThat(reporter.takeLocalSpan().tags())
      .isEmpty();
  }

  @Test public void nextSpan_should_clear_propagation_headers() {
    addB3MultiHeaders(fakeRecord);

    kafkaTracing.nextSpan(fakeRecord);
    assertThat(fakeRecord.headers().toArray()).isEmpty();
  }

  @Test public void nextSpan_should_not_clear_other_headers() {
    fakeRecord.headers().add("foo", new byte[0]);

    kafkaTracing.nextSpan(fakeRecord);
    assertThat(fakeRecord.headers().headers("foo")).isNotEmpty();
  }
}

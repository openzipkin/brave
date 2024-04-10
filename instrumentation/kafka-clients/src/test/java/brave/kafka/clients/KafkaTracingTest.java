/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.Span;
import brave.propagation.B3SingleFormat;
import brave.propagation.CurrentTraceContext.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.ITRemote.BAGGAGE_FIELD_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class KafkaTracingTest extends KafkaTest {
  @Test void nextSpan_prefers_b3_header() {
    consumerRecord.headers().add("b3", B3SingleFormat.writeB3SingleFormatAsBytes(incoming));

    Span child;
    try (Scope scope = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaTracing.nextSpan(consumerRecord);
    }
    child.finish();

    assertThat(spans.get(0).id()).isEqualTo(child.context().spanIdString());
    assertChildOf(spans.get(0), incoming);
  }

  @Test void nextSpan_uses_current_context() {
    Span child;
    try (Scope scope = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaTracing.nextSpan(consumerRecord);
    }
    child.finish();

    assertThat(spans.get(0).id()).isEqualTo(child.context().spanIdString());
    assertChildOf(spans.get(0), parent);
  }

  @Test void nextSpan_should_create_span_if_no_headers() {
    assertThat(kafkaTracing.nextSpan(consumerRecord)).isNotNull();
  }

  @Test void nextSpan_should_create_span_with_baggage() {
    addB3MultiHeaders(parent, consumerRecord);
    consumerRecord.headers().add(BAGGAGE_FIELD_KEY, "user1".getBytes());

    Span span = kafkaTracing.nextSpan(consumerRecord);
    assertThat(BAGGAGE_FIELD.getValue(span.context())).contains("user1");
  }

  @Test void nextSpan_should_tag_topic_and_key_when_no_incoming_context() {
    kafkaTracing.nextSpan(consumerRecord).start().finish();

    assertThat(spans.get(0).tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC), entry("kafka.key", TEST_KEY));
  }

  @Test void nextSpan_shouldnt_tag_null_key() {
    consumerRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1, null, TEST_VALUE);

    kafkaTracing.nextSpan(consumerRecord).start().finish();

    assertThat(spans.get(0).tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  @Test void nextSpan_shouldnt_tag_binary_key() {
    ConsumerRecord<byte[], String> record =
      new ConsumerRecord<>(TEST_TOPIC, 0, 1, new byte[1], TEST_VALUE);

    kafkaTracing.nextSpan(record).start().finish();

    assertThat(spans.get(0).tags())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  /**
   * We assume topic and key are already tagged by the producer span. However, we can change this
   * policy now, or later when dynamic policy is added to KafkaTracing
   */
  @Test void nextSpan_shouldnt_tag_topic_and_key_when_incoming_context() {
    addB3MultiHeaders(parent, consumerRecord);
    kafkaTracing.nextSpan(consumerRecord).start().finish();

    assertThat(spans.get(0).tags())
      .isEmpty();
  }

  @Test void nextSpan_should_clear_propagation_headers() {
    addB3MultiHeaders(parent, consumerRecord);

    kafkaTracing.nextSpan(consumerRecord);
    assertThat(consumerRecord.headers().toArray()).isEmpty();
  }

  @Test void nextSpan_should_retain_baggage_headers() {
    consumerRecord.headers().add(BAGGAGE_FIELD_KEY, new byte[0]);

    kafkaTracing.nextSpan(consumerRecord);
    assertThat(consumerRecord.headers().headers(BAGGAGE_FIELD_KEY)).isNotEmpty();
  }

  @Test void nextSpan_should_not_clear_other_headers() {
    consumerRecord.headers().add("foo", new byte[0]);

    kafkaTracing.nextSpan(consumerRecord);
    assertThat(consumerRecord.headers().headers("foo")).isNotEmpty();
  }
}

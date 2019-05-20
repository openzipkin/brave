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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TracingConsumerTest extends BaseTracingTest {
  MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
  TopicPartition topicPartition = new TopicPartition(TEST_TOPIC, 0);

  @Before
  public void before() {
    Map<TopicPartition, Long> offsets = new HashMap<>();
    offsets.put(topicPartition, 0L);

    consumer.updateBeginningOffsets(offsets);
    consumer.assign(offsets.keySet());
  }

  @Test
  public void should_call_wrapped_poll_and_close_spans() {
    consumer.addRecord(fakeRecord);
    Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);
    tracingConsumer.poll(10);

    // offset changed
    assertThat(consumer.position(topicPartition)).isEqualTo(2L);

    // name is correct
    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("poll");

    // kind is correct
    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(Span.Kind.CONSUMER);

    // tags are correct
    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", "myTopic"));
  }

  @Test
  public void should_call_wrapped_poll_and_close_spans_with_duration() {
    consumer.addRecord(fakeRecord);
    Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);
    tracingConsumer.poll(Duration.ofMillis(10));

    // offset changed
    assertThat(consumer.position(topicPartition)).isEqualTo(2L);

    // name is correct
    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("poll");

    // kind is correct
    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(Span.Kind.CONSUMER);

    // tags are correct
    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", "myTopic"));
  }

  @Test
  public void should_add_new_trace_headers_if_b3_missing() throws Exception {
    consumer.addRecord(fakeRecord);

    Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);
    ConsumerRecords<String, String> poll = tracingConsumer.poll(10);

    assertThat(poll)
      .extracting(ConsumerRecord::headers)
      .flatExtracting(TracingConsumerTest::lastHeaders)
      .extracting(Map.Entry::getKey)
      .contains("X-B3-TraceId", "X-B3-SpanId");
  }

  @Test
  public void should_createChildOfTraceHeaders() throws Exception {
    addB3Headers(fakeRecord);
    consumer.addRecord(fakeRecord);

    Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);
    ConsumerRecords<String, String> poll = tracingConsumer.poll(10);

    assertThat(poll)
      .extracting(ConsumerRecord::headers)
      .flatExtracting(TracingConsumerTest::lastHeaders)
      .contains(entry("X-B3-TraceId", TRACE_ID), entry("X-B3-ParentSpanId", SPAN_ID));
  }

  @Test
  public void should_create_only_one_consumer_span_per_topic() {
    Map<TopicPartition, Long> offsets = new HashMap<>();
    // 2 partitions in the same topic
    offsets.put(new TopicPartition(TEST_TOPIC, 0), 0L);
    offsets.put(new TopicPartition(TEST_TOPIC, 1), 0L);

    consumer.updateBeginningOffsets(offsets);
    consumer.assign(offsets.keySet());

    // create 500 messages
    for (int i = 0; i < 250; i++) {
      consumer.addRecord(new ConsumerRecord<>(TEST_TOPIC, 0, i, TEST_KEY, TEST_VALUE));
      consumer.addRecord(new ConsumerRecord<>(TEST_TOPIC, 1, i, TEST_KEY, TEST_VALUE));
    }

    Consumer<String, String> tracingConsumer = kafkaTracing.consumer(consumer);
    tracingConsumer.poll(10);

    // only one consumer span reported
    assertThat(spans)
      .hasSize(1)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", "myTopic"));
  }
}

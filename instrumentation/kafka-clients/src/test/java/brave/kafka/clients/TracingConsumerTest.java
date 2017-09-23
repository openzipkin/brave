package brave.kafka.clients;

import brave.Tracing;
import brave.sampler.Sampler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.internal.Util;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TracingConsumerTest {

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  String TRACE_ID = "463ac35c9f6413ad";
  String PARENT_ID = "463ac35c9f6413ab";
  String SPAN_ID = "48485a3953bb6124";
  String SAMPLED = "1";

  MockConsumer<String, String> consumer;
  TopicPartition topicPartition;
  Tracing tracing;

  List<Span> spans = new ArrayList<>();

  @Before
  public void init() throws IOException {
    tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();

    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    topicPartition = new TopicPartition(TEST_TOPIC, 0);

    Map<TopicPartition, Long> offsets = new HashMap<>();
    offsets.put(topicPartition, 0L);

    consumer.updateBeginningOffsets(offsets);
    consumer.assign(Collections.singleton(topicPartition));

    ConsumerRecord<String, String> fakeRecord =
        new ConsumerRecord<>(TEST_TOPIC, 0, 0, TEST_KEY, TEST_VALUE);
    fakeRecord.headers()
        .add("X-B3-TraceId", TRACE_ID.getBytes(Util.UTF_8))
        .add("X-B3-ParentSpanId", PARENT_ID.getBytes(Util.UTF_8))
        .add("X-B3-SpanId", SPAN_ID.getBytes(Util.UTF_8))
        .add("X-B3-Sampled", SAMPLED.getBytes(Util.UTF_8));
    consumer.addRecord(fakeRecord);
  }

  @After
  public void tearDown() throws Exception {
    spans.clear();
  }

  @Test
  public void should_call_wrapped_poll_and_close_spans() {
    Consumer<String, String> tracingConsumer = KafkaTracing.create(tracing).consumer(consumer);
    tracingConsumer.poll(10);

    // offset changed
    assertThat(consumer.position(topicPartition)).isEqualTo(1L);

    // new span from headers
    assertThat(spans)
        .extracting(Span::parentId)
        .containsExactly(SPAN_ID);

    // kind is correct
    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(Span.Kind.CONSUMER);

    // tags are correct
    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("kafka.key", "foo"), entry("kafka.topic", "myTopic"));
  }

  @Test
  public void should_add_new_trace_headers_if_b3_missing() throws Exception {
    consumer.addRecord(new ConsumerRecord<>(TEST_TOPIC, 0, 1, TEST_KEY, TEST_VALUE));
    Map<TopicPartition, Long> offsets = new HashMap<>();
    offsets.put(topicPartition, 1L);
    consumer.updateBeginningOffsets(offsets);

    Consumer<String, String> tracingConsumer = KafkaTracing.create(tracing).consumer(consumer);
    ConsumerRecords<String, String> poll = tracingConsumer.poll(10);

    assertThat(poll)
        .hasSize(1)
        .extracting(ConsumerRecord::headers)
        .extracting(headers -> headers.headers("X-B3-TraceId"))
        .isNotEmpty()
        .isNotEqualTo(TRACE_ID);
  }
}

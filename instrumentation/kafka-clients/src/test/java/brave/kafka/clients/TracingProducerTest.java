package brave.kafka.clients;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class TracingProducerTest extends BaseTracingTest {
  MockProducer<Object, String> mockProducer = new MockProducer<>();
  Producer<Object, String> tracingProducer = KafkaTracing.create(tracing).producer(mockProducer);

  @Test
  public void should_add_b3_headers_to_records() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

    List<String> headerKeys = mockProducer.history().stream()
        .flatMap(records -> Arrays.stream(records.headers().toArray()))
        .map(Header::key)
        .collect(Collectors.toList());

    List<String> expectedHeaders = Arrays.asList(
        "X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");

    assertThat(headerKeys).containsAll(expectedHeaders);
  }

  @Test
  public void should_call_wrapped_producer() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

    assertThat(mockProducer.history()).hasSize(1);
  }

  @Test
  public void send_should_set_name() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
        .flatExtracting(Span::name)
        .containsOnly("send");
  }

  @Test
  public void send_should_tag_topic_and_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(entry("kafka.topic", TEST_TOPIC), entry("kafka.key", TEST_KEY));
  }

  @Test
  public void send_shouldnt_tag_null_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, null, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  @Test
  public void send_shouldnt_tag_binary_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, new byte[1], TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }
}
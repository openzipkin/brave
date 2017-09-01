package brave.kafka.clients;

import brave.Tracing;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingProducerTest {

  private static final String TEST_TOPIC = "myTopic";
  private static final String TEST_KEY = "foo";
  private static final String TEST_VALUE = "bar";

  private Tracing tracing;

  @Before
  public void setup() throws Exception {
    tracing = Tracing.newBuilder().build();
  }

  @Test
  public void should_add_b3_headers_to_records() {
    MockProducer<String, String> mockProducer = new MockProducer<>();
    Producer<String, String> tracingProducer = KafkaTracing.create(tracing).producer(mockProducer);
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
    MockProducer<String, String> mockProducer = new MockProducer<>();
    Producer<String, String> tracingProducer = KafkaTracing.create(tracing).producer(mockProducer);
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

    assertThat(mockProducer.history()).hasSize(1);
  }
}
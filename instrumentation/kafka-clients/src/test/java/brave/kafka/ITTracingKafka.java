package brave.kafka;

import brave.Tracing;
import brave.sampler.Sampler;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Span;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ITTracingKafka {

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  Tracing consumerTracing;
  Tracing producerTracing;

  LinkedList<Span> consumerSpans = new LinkedList<>();
  LinkedList<Span> producerSpans = new LinkedList<>();

  @Rule
  public KafkaJunitRule kafkaRule = new KafkaJunitRule(EphemeralKafkaBroker.create());

  @Before
  public void setUp() throws Exception {
    consumerTracing = Tracing.newBuilder()
        .reporter(consumerSpans::add)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();
    producerTracing = Tracing.newBuilder()
        .reporter(producerSpans::add)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();
  }

  @Test
  public void produce_and_consume_kafka_message_continues_a_trace() throws Exception {
    Producer<String, String> tracingProducer = createTracingProducer();
    Consumer<String, String> tracingConsumer = createTracingConsumer();

    Future<RecordMetadata> send =
        tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    // Block for synchronous send
    send.get();

    ConsumerRecords<String, String> records = tracingConsumer.poll(10000);

    assertThat(records).hasSize(1);
    assertThat(producerSpans).hasSize(1);
    assertThat(consumerSpans).hasSize(1);

    assertThat(Long.toHexString(consumerSpans.getFirst().traceId))
        .isEqualTo(Long.toHexString(producerSpans.getFirst().traceId));

    KafkaTracing kafkaTracing = KafkaTracing.create(consumerTracing);
    for (ConsumerRecord<String, String> record : records) {
      brave.Span span = kafkaTracing.nextSpan(record);
      assertThat(span.context().parentId()).isEqualTo(producerSpans.getLast().traceId);
    }
  }

  Consumer<String, String> createTracingConsumer() {
    KafkaConsumer<String, String> consumer = kafkaRule.helper().createStringConsumer();
    consumer.assign(Collections.singleton(new TopicPartition(TEST_TOPIC, 0)));
    return KafkaTracing.create(consumerTracing).consumer(consumer);
  }

  Producer<String, String> createTracingProducer() {
    KafkaProducer<String, String> producer = kafkaRule.helper().createStringProducer();
    return KafkaTracing.create(producerTracing).producer(producer);
  }
}
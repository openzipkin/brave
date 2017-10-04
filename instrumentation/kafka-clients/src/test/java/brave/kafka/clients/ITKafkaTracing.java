package brave.kafka.clients;

import brave.Tracing;
import brave.internal.HexCodec;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import brave.sampler.Sampler;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.internal.Util.lowerHexToUnsignedLong;

public class ITKafkaTracing {

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  LinkedList<Span> consumerSpans = new LinkedList<>();
  LinkedList<Span> producerSpans = new LinkedList<>();

  KafkaTracing consumerTracing = KafkaTracing.create(Tracing.newBuilder()
      .spanReporter(consumerSpans::add)
      .sampler(Sampler.ALWAYS_SAMPLE)
      .build());
  KafkaTracing producerTracing = KafkaTracing.create(Tracing.newBuilder()
      .spanReporter(producerSpans::add)
      .sampler(Sampler.ALWAYS_SAMPLE)
      .build());

  @ClassRule
  public static KafkaJunitRule kafkaRule = new KafkaJunitRule(EphemeralKafkaBroker.create());
  @Rule
  public TestName testName = new TestName();

  @After
  public void close() throws Exception {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test
  public void continues_a_trace() throws Exception {
    Producer<String, String> tracingProducer = createTracingProducer();
    Consumer<String, String> tracingConsumer = createTracingConsumer();

    Future<RecordMetadata> send =
        tracingProducer.send(new ProducerRecord<>(testName.getMethodName(), TEST_KEY, TEST_VALUE));
    // Block for synchronous send
    send.get();

    ConsumerRecords<String, String> records = tracingConsumer.poll(10000);

    assertThat(records).hasSize(1);
    assertThat(producerSpans).hasSize(1);
    assertThat(consumerSpans).hasSize(1);

    assertThat(consumerSpans.getFirst().traceId())
        .isEqualTo(producerSpans.getFirst().traceId());

    for (ConsumerRecord<String, String> record : records) {
      TraceContext joined = consumerTracing.joinSpan(record).context();

      Span consumerSpan = consumerSpans.getLast();
      assertThat(joined.traceIdString()).isEqualTo(consumerSpan.traceId());
      assertThat(HexCodec.toLowerHex(joined.spanId())).isEqualTo(consumerSpan.id());
      assertThat(HexCodec.toLowerHex(joined.parentId())).isEqualTo(consumerSpan.parentId());
    }
  }

  static class TraceIdOnlyPropagation<K> implements Propagation<K> {
    final K key;

    TraceIdOnlyPropagation(Propagation.KeyFactory<K> keyFactory){
      key = keyFactory.create("x-b3-traceid");
    }

    @Override public List<K> keys() {
      return Collections.singletonList(key);
    }

    @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
      return (traceContext, carrier) -> setter.put(carrier, key, traceContext.traceIdString());
    }

    @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
      return carrier -> {
        String result = getter.get(carrier, key);
        if (result == null) return TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY);
        return TraceContextOrSamplingFlags.create(TraceIdContext.newBuilder()
            .traceId(lowerHexToUnsignedLong(result))
            .build());
      };
    }
  }

  @Test
  public void continues_a_trace_when_only_trace_id_propagated() throws Exception {
    consumerTracing = KafkaTracing.create(Tracing.newBuilder()
        .spanReporter(consumerSpans::add)
        .propagationFactory(new Propagation.Factory() {
          @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
            return new TraceIdOnlyPropagation<>(keyFactory);
          }
        })
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build());
    producerTracing = KafkaTracing.create(Tracing.newBuilder()
        .spanReporter(producerSpans::add)
        .propagationFactory(new Propagation.Factory() {
          @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
            return new TraceIdOnlyPropagation<>(keyFactory);
          }
        })
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build());

    Producer<String, String> tracingProducer = createTracingProducer();
    Consumer<String, String> tracingConsumer = createTracingConsumer();

    Future<RecordMetadata> send =
        tracingProducer.send(new ProducerRecord<>(testName.getMethodName(), TEST_KEY, TEST_VALUE));
    // Block for synchronous send
    send.get();

    ConsumerRecords<String, String> records = tracingConsumer.poll(10000);

    assertThat(records).hasSize(1);
    assertThat(producerSpans).hasSize(1);
    assertThat(consumerSpans).hasSize(1);

    assertThat(consumerSpans.getFirst().traceId())
        .isEqualTo(producerSpans.getFirst().traceId());

    for (ConsumerRecord<String, String> record : records) {
      TraceContext joined = consumerTracing.joinSpan(record).context();

      Span consumerSpan = consumerSpans.getLast();
      assertThat(joined.traceIdString()).isEqualTo(consumerSpan.traceId());
    }
  }

  Consumer<String, String> createTracingConsumer() {
    KafkaConsumer<String, String> consumer = kafkaRule.helper().createStringConsumer();
    consumer.assign(Collections.singleton(new TopicPartition(testName.getMethodName(), 0)));
    return consumerTracing.consumer(consumer);
  }

  Producer<String, String> createTracingProducer() {
    KafkaProducer<String, String> producer = kafkaRule.helper().createStringProducer();
    return producerTracing.producer(producer);
  }
}
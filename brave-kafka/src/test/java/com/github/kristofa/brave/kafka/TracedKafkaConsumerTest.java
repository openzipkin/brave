package com.github.kristofa.brave.kafka;

import com.github.charithe.kafka.KafkaJunitRule;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Span;
import java.util.LinkedList;
import java.util.List;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.assertj.core.api.Assertions.assertThat;

public class TracedKafkaConsumerTest {
  @ClassRule
  public static KafkaJunitRule kafka = new KafkaJunitRule();
  @Rule public TestName testName = new TestName();
  List<Span> spans = new LinkedList<>();
  Brave brave = new Brave.Builder().spanCollector(new SpanCollector() {
    @Override public void collect(Span span) {
      spans.add(span);
    }

    @Override public void addDefaultAnnotation(String key, String value) {
    }
  }).build();

  TracedKafkaConsumer consumer =
      new TracedKafkaConsumer(brave, kafka.consumerConfig().props().props());

  @Test public void usesPropagatedId() throws Exception {
    String topic = testName.getMethodName();
    Producer producer = new Producer(kafka.producerConfigWithDefaultEncoder());

    // assume the span propagated is the child span we are to join
    SpanId producerSpanId = SpanId.builder()
        .traceId(1L)
        .parentId(1L)
        .spanId(2L)
        .sampled(true).build();

    producer.send(
        new KeyedMessage<byte[], byte[]>(topic, producerSpanId.bytes(), "foo".getBytes()));
    producer.close();

    assertThat(new String(consumer.iterator(topic).next())).isEqualTo("foo");
    consumer.close();

    Span consumerSpan = spans.get(0);

    assertThat(consumerSpan.getAnnotations()).extracting(a -> a.value)
        .containsOnly("mr", "wr");
    assertThat(consumerSpan.getBinary_annotations()).extracting(b -> b.key)
        .containsOnly("kafka.partition");

    // In this scenario, we use the same span to cover time spent in kafka
    assertThat(producerSpanId.traceId).isEqualTo(consumerSpan.getTrace_id());
    assertThat(producerSpanId.nullableParentId()).isEqualTo(consumerSpan.getParent_id());
    assertThat(producerSpanId.spanId).isEqualTo(consumerSpan.getId());
  }
}

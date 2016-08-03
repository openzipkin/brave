package com.github.kristofa.brave.kafka;

import com.github.charithe.kafka.KafkaJunitRule;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.google.common.collect.ImmutableMap;
import com.twitter.zipkin.gen.Span;
import java.util.LinkedList;
import java.util.List;
import kafka.consumer.Consumer;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.assertj.core.api.Assertions.assertThat;

public class TracedKafkaProducerTest {
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

  TracedKafkaProducer producer =
      new TracedKafkaProducer(brave, kafka.producerConfigWithStringEncoder().props().props());

  @Test
  public void sendsSpanAndPropagatesSpanId() throws Exception {
    String topic = testName.getMethodName();

    producer.send(topic, "foo".getBytes()).get();
    producer.close();

    assertThat(spans).hasSize(1);

    List<String> messages = kafka.readStringMessages(topic, 1);
    assertThat(messages).hasSize(1);
  }

  @Test public void propagatesId() throws Exception {
    String topic = testName.getMethodName();

    producer.send(topic, "foo".getBytes()).get();
    producer.close();

    ConsumerConnector connector =
        Consumer.createJavaConsumerConnector(kafka.consumerConfig());

    KafkaStream<byte[], byte[]> stream =
        connector.createMessageStreams(ImmutableMap.of(topic, 1)).values().iterator().next().get(0);

    MessageAndMetadata<byte[], byte[]> message = stream.iterator().next();
    connector.shutdown();
    assertThat(new String(message.message())).isEqualTo("foo");

    SpanId consumerSpanId = SpanId.fromBytes(message.key());
    Span producerSpan = spans.get(0);

    assertThat(producerSpan.getAnnotations()).extracting(a -> a.value)
        .containsOnly("ms", "ws");
    assertThat(producerSpan.getBinary_annotations()).extracting(b -> b.key)
        .containsOnly("kafka.topic");

    // In this scenario, we use the same span to cover time spent in kafka
    assertThat(consumerSpanId.traceId).isEqualTo(producerSpan.getTrace_id());
    assertThat(consumerSpanId.nullableParentId()).isEqualTo(producerSpan.getParent_id());
    assertThat(consumerSpanId.spanId).isEqualTo(producerSpan.getId());
    assertThat(consumerSpanId.sampled()).isNull(); // TODO: add sampling step
  }
}

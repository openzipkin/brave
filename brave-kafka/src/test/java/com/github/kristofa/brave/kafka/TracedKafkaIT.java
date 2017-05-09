package com.github.kristofa.brave.kafka;

import com.github.charithe.kafka.KafkaJunitRule;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.http.HttpSpanCollector;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

// run this when you have zipkin running
public class TracedKafkaIT {
  @ClassRule
  public static KafkaJunitRule kafka = new KafkaJunitRule();
  @Rule public TestName testName = new TestName();
  Brave brave = new Brave.Builder()
      .spanCollector(HttpSpanCollector.create("http://localhost:9411/",
          new EmptySpanCollectorMetricsHandler()))
      .build();

  TracedKafkaProducer producer =
      new TracedKafkaProducer(brave, kafka.producerConfigWithStringEncoder().props().props());

  TracedKafkaConsumer consumer =
      new TracedKafkaConsumer(brave, kafka.consumerConfig().props().props());

  // after this test, look at http://localhost:9411/
  @Test
  public void roundTrip() throws Exception {
    String topic = testName.getMethodName();

    producer.send(topic, "foo".getBytes()).get();
    producer.close();

    consumer.iterator(topic).next();
    consumer.close();

    Thread.sleep(1500L); // span collector
  }
}

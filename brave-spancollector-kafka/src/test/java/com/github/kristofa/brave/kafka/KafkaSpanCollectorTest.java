package com.github.kristofa.brave.kafka;

import com.github.charithe.kafka.KafkaJunitRule;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.github.kristofa.brave.kafka.KafkaSpanCollector.Config;
import com.twitter.zipkin.gen.Span;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import kafka.serializer.DefaultDecoder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Codec;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaSpanCollectorTest {

  @Rule
  public KafkaJunitRule kafka = new KafkaJunitRule();
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  TestMetricsHander metrics = new TestMetricsHander();
  // set flush interval to 0 so that tests can drive flushing explicitly
  Config config = Config.builder("localhost:" + kafka.kafkaBrokerPort()).flushInterval(0).build();
  KafkaSpanCollector collector = new KafkaSpanCollector(config, metrics);

  @After
  public void closeCollector(){
    collector.close();
  }

  @Test
  public void collectDoesntDoIO() throws Exception {
    thrown.expect(TimeoutException.class);
    collector.collect(span(1L, "foo"));

    assertThat(readMessages()).isEmpty();
  }

  @Test
  public void collectIncrementsAcceptedMetrics() throws Exception {
    collector.collect(span(1L, "foo"));

    assertThat(metrics.acceptedSpans.get()).isEqualTo(1);
    assertThat(metrics.droppedSpans.get()).isZero();
  }

  @Test
  public void dropsWhenQueueIsFull() throws Exception {
    for (int i = 0; i < 1001; i++)
      collector.collect(span(1L, "foo"));

    collector.flush(); // manually flush the spans

    assertThat(Codec.THRIFT.readSpans(readMessages().get(0))).hasSize(1000);
    assertThat(metrics.droppedSpans.get()).isEqualTo(1);
  }

  @Test
  public void sendsSpans() throws Exception {
    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    // Ensure only one message was sent
    List<byte[]> messages = readMessages();
    assertThat(messages).hasSize(1);

    // Now, let's read back the spans we sent!
    assertThat(Codec.THRIFT.readSpans(messages.get(0))).containsExactly(
        zipkinSpan(1L, "foo"),
        zipkinSpan(2L, "bar")
    );
  }

  @Test
  public void submitMultipleSpansInParallel() throws Exception {
    Callable<Void> spanProducer1 = () -> {
      for (int i = 1; i <= 200; i++) {
        collector.collect(span(i, "producer1_" + i));
      }
      return null;
    };

    Callable<Void> spanProducer2 = () -> {
      for (int i = 1; i <= 200; i++) {
        collector.collect(span(i, "producer2_" + i));
      }
      return null;
    };

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Future<Void> future1 = executorService.submit(spanProducer1);
    Future<Void> future2 = executorService.submit(spanProducer2);

    future1.get(2000, TimeUnit.MILLISECONDS);
    future2.get(2000, TimeUnit.MILLISECONDS);

    collector.flush(); // manually flush the spans

    // Ensure only one message was sent
    List<byte[]> messages = readMessages();
    assertThat(messages).hasSize(1);

    // Now, let's make sure we read the correct count of spans.
    assertThat(Codec.THRIFT.readSpans(messages.get(0))).hasSize(400);
  }

  @Test
  public void submitsSpansToCorrectTopic() throws Exception {
    Config config = Config.builder("localhost:" + kafka.kafkaBrokerPort()).topic("customzipkintopic").build();
    KafkaSpanCollector collector = new KafkaSpanCollector(config, metrics);
    collector.collect(span(123, "myspan"));
    List<byte[]> messages = readMessages("customzipkintopic");
    assertThat(messages).hasSize(1);
  }

  class TestMetricsHander implements SpanCollectorMetricsHandler {

    final AtomicInteger acceptedSpans = new AtomicInteger();
    final AtomicInteger droppedSpans = new AtomicInteger();

    @Override
    public void incrementAcceptedSpans(int quantity) {
      acceptedSpans.addAndGet(quantity);
    }

    @Override
    public void incrementDroppedSpans(int quantity) {
      droppedSpans.addAndGet(quantity);
    }
  }

  static Span span(long traceId, String spanName) {
    return new Span().setTrace_id(traceId).setId(traceId).setName(spanName);
  }

  static zipkin.Span zipkinSpan(long traceId, String spanName) {
    return zipkin.Span.builder().traceId(traceId).id(traceId).name(spanName).build();
  }

  private List<byte[]> readMessages(String topic) throws TimeoutException {
    return kafka.readMessages(topic, 1, new DefaultDecoder(kafka.consumerConfig().props()));
  }
  private List<byte[]> readMessages() throws TimeoutException {
    return readMessages("zipkin");
  }
}

package brave.kafka.streams;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITKafkaStreamsTracing {
  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule(EphemeralKafkaBroker.create());
  @Rule public TestName testName = new TestName();

  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

  KafkaTracing kafkaTracing = KafkaTracing.create(Tracing.newBuilder()
      .localServiceName("streams-app")
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .spanReporter(spans::add)
      .build());
  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(kafkaTracing);

  Producer<String, String> producer;
  Consumer<String, String> consumer;

  @Rule public TestRule assertSpansEmpty = new TestWatcher() {
    // only check success path to avoid masking assertion errors or exceptions
    @Override protected void succeeded(Description description) {
      try {
        assertThat(spans.poll(100, TimeUnit.MILLISECONDS))
            .withFailMessage("Stream span remaining in queue. Check for redundant reporting")
            .isNull();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  };

  @After public void close() {
    if (producer != null) producer.close();
    if (consumer != null) consumer.close();
    Tracing tracing = Tracing.current();
    if (tracing != null) tracing.close();
  }

  @Test public void should_create_span_from_input_topic() throws Exception {
    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    streams.start();

    //TODO fix waiting time for kafka streams to be running
    do {
      Thread.sleep(1_000);
    } while (!streams.state().isRunning());

    Span span = takeSpan();

    assertThat(span.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    //streams.cleanUp();
  }

  @Test  public void should_create_spans_from_input_to_output() throws Exception {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).to(outputTopic);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    consumer = createTracingConsumer(outputTopic);
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    streams.start();

    //TODO fix waiting time for kafka streams to be running
    do {
      Thread.sleep(1_000);
    } while (!streams.state().isRunning());

    //ConsumerRecords<String, String> consumerRecords =
    //    consumer.poll(Duration.ofMillis(100));
    //assertThat(consumerRecords).hasSize(1);

    Span spanInput = takeSpan(), spanOutput = takeSpan();

    assertThat(spanInput.traceId()).isEqualTo(spanOutput.traceId());
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);

    streams.close();
    //streams.cleanUp();
  }

  KafkaStreams buildKafkaStreams(Topology topology) {
    Properties properties = new Properties();
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafka.helper().consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    properties.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, testName.getMethodName());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    return new KafkaStreams(topology, properties, kafkaStreamsTracing.kafkaClientSupplier());
  }

  Producer<String, String> createTracingProducer() {
    return kafka.helper().createStringProducer();
  }

  Consumer<String, String> createTracingConsumer(String... topics) {
    if (topics.length == 0) {
      topics = new String[] {testName.getMethodName()};
    }
    KafkaConsumer<String, String> consumer = kafka.helper().createStringConsumer();
    List<TopicPartition> assignments = new ArrayList<>();
    for (String topic : topics) {
      assignments.add(new TopicPartition(topic, 0));
    }
    consumer.assign(assignments);
    return kafkaTracing.consumer(consumer);
  }

  /**
   * Call this to block until a span was reported
   */
  Span takeSpan() throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
        .withFailMessage("Stream span was not reported")
        .isNotNull();
    return result;
  }
}

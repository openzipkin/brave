package brave.kafka.streams;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.StrictCurrentTraceContext;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public class ITKafkaClientSupplier {

  private String TEST_KEY = "foo";
  private String TEST_VALUE = "bar";

  private BlockingQueue<Span> streamsSpans = new LinkedBlockingQueue<>();

  private KafkaTracing kafkaTracing = KafkaTracing.create(Tracing.newBuilder()
      .localServiceName("streams-app")
      .currentTraceContext(new StrictCurrentTraceContext())
      .spanReporter(streamsSpans::add)
      .build());

  @ClassRule
  public static KafkaJunitRule kafkaRule = new KafkaJunitRule(EphemeralKafkaBroker.create());
  @Rule
  public TestName testName = new TestName();

  private TracingKafkaClientSupplier supplier = new TracingKafkaClientSupplier(kafkaTracing);

  private Producer<String, String> producer;
  private Consumer<String, String> consumer;

  @Rule public TestRule assertSpansEmpty = new TestWatcher() {
    // only check success path to avoid masking assertion errors or exceptions
    @Override protected void succeeded(Description description) {
      try {
        assertThat(streamsSpans.poll(100, TimeUnit.MILLISECONDS))
            .withFailMessage("Stream span remaining in queue. Check for redundant reporting")
            .isNull();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  };

  @After
  public void close() {
    if (producer != null) {
      producer.close();
    }
    if (consumer != null) {
      consumer.close();
    }
    Tracing tracing = Tracing.current();
    if (tracing != null) {
      tracing.close();
    }
  }

  @Test
  public void should_create_span_from_input_topic() throws Exception {
    final String inputTopic = testName.getMethodName() + "-input";
    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic)
        .foreach((k, v) -> {
        });
    Topology topology = builder.build();
    Properties streamsConfig = new Properties();
    streamsConfig.put(
        StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaRule.helper().consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, testName.getMethodName());
    streamsConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    KafkaStreams streams = new KafkaStreams(topology, streamsConfig, supplier);

    producer = createTracingProducer();

    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    streams.start();

    //TODO fix waiting time for kafka streams to be running
    do {
      Thread.sleep(1_000);
    } while (!streams.state().isRunning());

    Span span = takeStreamsSpan();

    assertNotNull(span);

    streams.close();
    //streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_input_to_output() throws Exception {
    final String inputTopic = testName.getMethodName() + "-input";
    final String outputTopic = testName.getMethodName() + "-output";
    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).to(outputTopic);
    Topology topology = builder.build();

    Properties streamsConfig = new Properties();
    streamsConfig.put(
        StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaRule.helper().consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, testName.getMethodName());
    streamsConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    KafkaStreams streams = new KafkaStreams(topology, streamsConfig, supplier);

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

    Span spanInput = takeStreamsSpan(), spanOutput = takeStreamsSpan();

    assertNotNull(spanInput);
    assertNotNull(spanOutput);
    assertThat(spanInput.traceId()).isEqualTo(spanOutput.traceId());

    streams.close();
    //streams.cleanUp();
  }


  private Producer<String, String> createTracingProducer() {
    return kafkaRule.helper().createStringProducer();
  }

  private Consumer<String, String> createTracingConsumer(String... topics) {
    if (topics.length == 0) {
      topics = new String[] {testName.getMethodName()};
    }
    KafkaConsumer<String, String> consumer = kafkaRule.helper().createStringConsumer();
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
  private Span takeStreamsSpan() throws InterruptedException {
    Span result = streamsSpans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
        .withFailMessage("Stream span was not reported")
        .isNotNull();
    return result;
  }
}

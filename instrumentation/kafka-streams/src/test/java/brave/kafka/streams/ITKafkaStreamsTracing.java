package brave.kafka.streams;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import zipkin2.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ITKafkaStreamsTracing {
  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule(EphemeralKafkaBroker.create());
  @Rule public TestName testName = new TestName();

  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

  Tracing tracing = Tracing.newBuilder()
      .localServiceName("streams-app")
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .spanReporter(spans::add)
      .build();
  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);

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

  @Test
  public void should_create_span_from_stream_input_topic() throws Exception {
    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    waitForStreamToRun(streams);

    Span span = takeSpan();

    assertThat(span.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_input_and_output_topics() throws Exception {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).to(outputTopic);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    consumer = createTracingConsumer(outputTopic);

    waitForStreamToRun(streams);

    Span spanInput = takeSpan(), spanOutput = takeSpan();

    assertThat(spanInput.traceId()).isEqualTo(spanOutput.traceId());
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_processor() throws Exception {
    ProcessorSupplier<String, String> processorSupplier =
            kafkaStreamsTracing.processorSupplier(
                    "processor-1",
                    new AbstractProcessor<String, String>() {
                      @Override
                      public void process(String key, String value) {
                        try {
                          Thread.sleep(100L);
                        } catch (InterruptedException e) {
                          e.printStackTrace();
                        }
                      }
                    });

    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
            .process(processorSupplier);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    waitForStreamToRun(streams);

    Span spanInput = takeSpan(), spanProcessor = takeSpan();

    assertThat(spanInput.traceId()).isEqualTo(spanProcessor.traceId());
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_transformer() throws Exception {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
            kafkaStreamsTracing.transformerSupplier(
                    "transformer-1",
                    new Transformer<String, String, KeyValue<String, String>>() {
                      ProcessorContext context;

                      @Override
                      public void init(ProcessorContext context) {
                        this.context = context;
                      }

                      @Override
                      public KeyValue<String, String> transform(String key, String value) {
                        try {
                          Thread.sleep(100L);
                        } catch (InterruptedException e) {
                          e.printStackTrace();
                        }
                        return KeyValue.pair(key, value);
                      }

                      @Override
                      public void close() {
                      }
                    });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
            .transform(transformerSupplier)
            .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    producer = createTracingProducer();
    producer.send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE)).get();

    waitForStreamToRun(streams);

    Span spanInput = takeSpan(), spanProcessor = takeSpan(), spanOutput = takeSpan();

    assertThat(spanInput.traceId()).isEqualTo(spanProcessor.traceId());
    assertThat(spanProcessor.traceId()).isEqualTo(spanOutput.traceId());
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);

    streams.close();
    streams.cleanUp();
  }

  private void waitForStreamToRun(KafkaStreams streams) throws InterruptedException {
    streams.start();

    do {
      Thread.sleep(1_000);
    } while (!streams.state().isRunning());
  }

  KafkaStreams buildKafkaStreams(Topology topology) {
    Properties properties = new Properties();
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafka.helper().consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    properties.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, testName.getMethodName());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    return kafkaStreamsTracing.kafkaStreams(topology, properties);
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
    return KafkaTracing.create(tracing).consumer(consumer);
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

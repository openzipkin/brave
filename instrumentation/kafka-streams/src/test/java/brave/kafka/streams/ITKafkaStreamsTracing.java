/*
 * Copyright 2013-2024 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.kafka.streams;

import brave.handler.MutableSpan;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingTracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static brave.Span.Kind.CONSUMER;
import static brave.Span.Kind.PRODUCER;
import static brave.kafka.streams.KafkaStreamsTracingTest.TEST_KEY;
import static brave.kafka.streams.KafkaStreamsTracingTest.TEST_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(60)
class ITKafkaStreamsTracing extends ITKafkaStreams {
  @Container KafkaContainer kafka = new KafkaContainer();

  Producer<String, String> producer;

  @BeforeEach void initProducer() {
    producer = createProducer();
  }

  @Override @AfterEach protected void close() throws Exception {
    if (producer != null) producer.close();
    super.close();
  }

  @Test void should_create_span_from_stream_input_topic() {
    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test void should_create_multiple_span_from_stream_input_topic_whenSharingDisabled() {
    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.newBuilder(tracing)
      .singleRootSpanOnReceiveBatch(false)
      .build();
    KafkaStreams streams = kafkaStreamsTracing.kafkaStreams(topology, streamsProperties());

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));
    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));
    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    for (int i = 0; i < 3; i++) {
      MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
      assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);
    }

    streams.close();
    streams.cleanUp();
  }

  @Test void should_create_one_span_from_stream_input_topic_whenSharingEnabled() {
    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    MessagingTracing messagingTracing = MessagingTracing.create(tracing);
    KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.newBuilder(messagingTracing)
      .singleRootSpanOnReceiveBatch(true)
      .build();
    KafkaStreams streams = kafkaStreamsTracing.kafkaStreams(topology, streamsProperties());

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));
    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));
    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test void should_create_span_from_stream_input_topic_using_kafka_client_supplier() {
    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams =
      new KafkaStreams(topology, streamsProperties(), kafkaStreamsTracing.kafkaClientSupplier());

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test void should_create_spans_from_stream_input_and_output_topics() {
    String inputTopic = testName + "-input";
    String outputTopic = testName + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).to(outputTopic);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    Consumer<String, String> consumer = createTracingConsumer(outputTopic);

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    MutableSpan spanOutput = testSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanInput);

    streams.close();
    streams.cleanUp();
    consumer.close();
  }

  @Test void should_create_spans_from_stream_with_tracing_processor() {
    ProcessorSupplier<String, String, String, String> processorSupplier =
      kafkaStreamsTracing.process("forward-1", () -> record -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });

    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .process(processorSupplier);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    MutableSpan spanProcessor = testSpanHandler.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    streams.close();
    streams.cleanUp();
  }

  @Test void should_create_spans_from_stream_with_tracing_fixed_key_processor() {
    FixedKeyProcessorSupplier<String, String, String> processorSupplier =
      kafkaStreamsTracing.processValues("forward-1", () -> record -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });

    String inputTopic = testName + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .processValues(processorSupplier);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    MutableSpan spanInput = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    MutableSpan spanProcessor = testSpanHandler.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    streams.close();
    streams.cleanUp();
  }

  @Test void injectsInitContextOnForward() {
    String inputTopic = testName + "-input";

    BlockingDeque<TraceContext> contexts = new LinkedBlockingDeque<>();

    StreamsBuilder builder = new StreamsBuilder();
    builder.<String, String>stream(inputTopic)
      .process(kafkaStreamsTracing.process("forward",
        () -> new ContextualProcessor<String, String, String, String>() {
          @Override public void process(Record<String, String> record) {
            context().forward(record);
          }
        }))
      .process(kafkaStreamsTracing.process("check",
        () -> (Processor<String, String, String, String>) record -> {
          contexts.add(currentTraceContext.get());
        }));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    // Simulate a trace that started from an external producer
    TraceContext root = newTraceContext(SamplingFlags.SAMPLED);
    ProducerRecord<String, String> record = new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE);
    kafkaStreamsTracing.injector.inject(root, record.headers());
    send(record);

    waitForStreamToRun(streams);

    MutableSpan spanPoll = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(spanPoll.name()).isEqualTo("poll");
    assertThat(spanPoll.tags()).containsEntry("kafka.topic", inputTopic);

    MutableSpan spanCheck = testSpanHandler.takeLocalSpan();
    assertThat(spanCheck.name()).isEqualTo("check");
    assertThat(spanCheck.tags()).doesNotContainKey("kafka.topic"); // not remote

    MutableSpan spanForward = testSpanHandler.takeLocalSpan();
    assertThat(spanForward.name()).isEqualTo("forward");
    assertThat(spanCheck.tags()).doesNotContainKey("kafka.topic"); // not remote

    // All spans are in the same trace
    assertChildOf(spanCheck, spanForward);
    assertChildOf(spanForward, spanPoll);
    assertChildOf(spanPoll, root);

    // The terminal processor had the right span in context
    TraceContext contextCheck = contexts.poll();
    assertThat(contextCheck).isNotNull();
    assertSameIds(spanCheck, contextCheck);

    streams.close();
    streams.cleanUp();
  }

  private void waitForStreamToRun(KafkaStreams streams) {
    streams.start();

    do {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    } while (!streams.state().isRunningOrRebalancing());
  }

  KafkaStreams buildKafkaStreams(Topology topology) {
    Properties properties = streamsProperties();
    return kafkaStreamsTracing.kafkaStreams(topology, properties);
  }

  Properties streamsProperties() {
    Properties properties = new Properties();
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafka.consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    properties.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, testName);
    properties.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
      Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
      Serdes.String().getClass().getName());
    properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
      Serdes.String().getClass().getName());
    return properties;
  }

  Producer<String, String> createProducer() {
    return kafka.createStringProducer();
  }

  Consumer<String, String> createTracingConsumer(String... topics) {
    if (topics.length == 0) {
      topics = new String[] {testName};
    }
    KafkaConsumer<String, String> consumer = kafka.createStringConsumer();
    List<TopicPartition> assignments = new ArrayList<>();
    for (String topic : topics) {
      assignments.add(new TopicPartition(topic, 0));
    }
    consumer.assign(assignments);
    return KafkaTracing.create(tracing).consumer(consumer);
  }

  void send(ProducerRecord<String, String> record) {
    BlockingCallback callback = new BlockingCallback();
    producer.send(record, callback);
    callback.join();
  }
}

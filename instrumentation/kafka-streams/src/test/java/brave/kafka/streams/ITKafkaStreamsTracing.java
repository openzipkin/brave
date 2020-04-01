/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingTracing;
import brave.propagation.TraceContext;
import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import static brave.kafka.streams.KafkaStreamsTags.KAFKA_STREAMS_FILTERED_TAG;
import static org.assertj.core.api.Assertions.assertThat;

public class ITKafkaStreamsTracing extends ITKafkaStreams {
  @ClassRule public static KafkaJunitRule kafka = new KafkaJunitRule(EphemeralKafkaBroker.create());

  Producer<String, String> producer = createProducer();

  @After public void close() {
    producer.close();
  }

  @Test
  public void should_create_span_from_stream_input_topic() {
    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_multiple_span_from_stream_input_topic_whenSharingDisabled() {
    String inputTopic = testName.getMethodName() + "-input";

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
      Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
      assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);
    }

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_one_span_from_stream_input_topic_whenSharingEnabled() {
    String inputTopic = testName.getMethodName() + "-input";

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

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_span_from_stream_input_topic_using_kafka_client_supplier() {
    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).foreach((k, v) -> {
    });
    Topology topology = builder.build();

    KafkaStreams streams =
      new KafkaStreams(topology, streamsProperties(), kafkaStreamsTracing.kafkaClientSupplier());

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_input_and_output_topics() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic).to(outputTopic);
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    Consumer<String, String> consumer = createTracingConsumer(outputTopic);

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanInput);

    streams.close();
    streams.cleanUp();
    consumer.close();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_processor() {
    ProcessorSupplier<String, String> processorSupplier =
      kafkaStreamsTracing.processor(
        "forward-1", () ->
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

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_predicate_true() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transform(kafkaStreamsTracing.filter("filter-1", (key, value) -> true))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_predicate_false() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transform(kafkaStreamsTracing.filter("filter-2", (key, value) -> false))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filter transformer returns false so record is dropped

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_and_propagate_extra_from_stream_with_multi_processor() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.peek("transform1", (o, o2) -> {
        TraceContext context = currentTraceContext.get();
        assertThat(BAGGAGE_FIELD.getValue(context)).isEqualTo("user1");
        BAGGAGE_FIELD.updateValue(context, "user2");
      }))
      .transformValues(kafkaStreamsTracing.peek("transform2", (s, s2) -> {
        TraceContext context = currentTraceContext.get();
        assertThat(BAGGAGE_FIELD.getValue(context)).isEqualTo("user2");
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    ProducerRecord<String, String> record = new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE);
    record.headers().add(BAGGAGE_FIELD.name(), "user1".getBytes());
    send(record);

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanTransform1 = reporter.takeLocalSpan();
    assertChildOf(spanTransform1, spanInput);

    Span spanTransform2 = reporter.takeLocalSpan();
    assertChildOf(spanTransform2, spanTransform1);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanTransform2);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_not_predicate_true() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transform(kafkaStreamsTracing.filterNot("filterNot-1", (key, value) -> true))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filterNot transformer returns true so record is dropped

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_not_predicate_false() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transform(kafkaStreamsTracing.filterNot("filterNot-2", (key, value) -> false))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_filtered_predicate_true() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.markAsFiltered("filter-1", (key, value) -> true))
      .filterNot((k, v) -> Objects.isNull(v))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_filtered_predicate_false() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.markAsFiltered("filter-2", (key, value) -> false))
      .filterNot((k, v) -> Objects.isNull(v))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filter transformer returns false so record is dropped

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_not_filtered_predicate_true() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.markAsNotFiltered("filterNot-1", (key, value) -> true))
      .filterNot((k, v) -> Objects.isNull(v))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filterNot transformer returns true so record is dropped

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_not_filtered_predicate_false() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(
        kafkaStreamsTracing.markAsNotFiltered("filterNot-2", (key, value) -> false))
      .filterNot((k, v) -> Objects.isNull(v))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags()).containsEntry(KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_peek() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    long now = System.currentTimeMillis();

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.peek("peek-1", (key, value) -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        tracing.tracer().currentSpan().annotate(now, "test");
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.annotations()).contains(Annotation.create(now, "test"));

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.mark("mark-1"))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_foreach() {
    String inputTopic = testName.getMethodName() + "-input";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .process(kafkaStreamsTracing.foreach("foreach-1", (key, value) -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_and_tracing_processor() {
    ProcessorSupplier<String, String> processorSupplier =
      kafkaStreamsTracing.processor(
        "forward-1", () ->
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

    KafkaStreams streams = buildKafkaStreamsWithoutTracing(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_transformer() {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "transformer-1", () ->
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

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_throw_exception_upwards() {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "exception-transformer", () ->
          new Transformer<String, String, KeyValue<String, String>>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public KeyValue<String, String> transform(String key, String value) {
              throw new IllegalArgumentException("illegal-argument");
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

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpanWithError("illegal-argument");
    assertChildOf(spanProcessor, spanInput);

    assertThat(!streams.state().isRunning());

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_throw_sneaky_exception_upwards() {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "sneaky-exception-transformer", () ->
          new Transformer<String, String, KeyValue<String, String>>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public KeyValue<String, String> transform(String key, String value) {
              doThrowUnsafely(new FileNotFoundException("file-not-found"));
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

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpanWithError("file-not-found");
    assertChildOf(spanProcessor, spanInput);

    assertThat(!streams.state().isRunning());

    streams.close();
    streams.cleanUp();
  }

  // Armeria Black Magic copied from
  // https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/util/Exceptions.java#L197
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void doThrowUnsafely(Throwable cause) throws E {
    throw (E) cause;
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_flattransformer() {
    TransformerSupplier<String, String, Iterable<KeyValue<String, String>>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "double-transformer-1", () ->
          new Transformer<String, String, Iterable<KeyValue<String, String>>>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public Iterable<KeyValue<String, String>> transform(String key, String value) {
              try {
                Thread.sleep(100L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              return Arrays.asList(KeyValue.pair(key, value), KeyValue.pair(key, value));
            }

            @Override
            public void close() {
            }
          });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .flatTransform(transformerSupplier)
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    for (int i = 0; i < 2; i++) {
      Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
      assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
      assertChildOf(spanOutput, spanProcessor);
    }

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_transformer() {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "transformer-1", () ->
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

    KafkaStreams streams = buildKafkaStreamsWithoutTracing(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_valueTransformer() {
    ValueTransformerSupplier<String, String> transformerSupplier =
      kafkaStreamsTracing.valueTransformer(
        "transformer-1", () ->
          new ValueTransformer<String, String>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public String transform(String value) {
              try {
                Thread.sleep(100L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              return value;
            }

            @Override
            public void close() {
            }
          });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(transformerSupplier)
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_map() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transform(kafkaStreamsTracing.map("map-1", (key, value) -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return KeyValue.pair(key, value);
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_flatMap() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .flatTransform(kafkaStreamsTracing.flatMap("flat-map-1", (key, value) -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return Arrays.asList(KeyValue.pair(key, value + "-1"), KeyValue.pair(key, value + "-2"));
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    for (int i = 0; i < 2; i++) {
      Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
      assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
      assertChildOf(spanOutput, spanProcessor);
    }

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mapValues() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.mapValues("mapValue-1", value -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return value;
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mapValues_withKey() {
    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(kafkaStreamsTracing.mapValues("mapValue-1", (key, value) -> {
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        return value;
      }))
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_valueTransformer() {
    ValueTransformerSupplier<String, String> transformerSupplier =
      kafkaStreamsTracing.valueTransformer(
        "transformer-1", () ->
          new ValueTransformer<String, String>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public String transform(String value) {
              try {
                Thread.sleep(100L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              return value;
            }

            @Override
            public void close() {
            }
          });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(transformerSupplier)
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreamsWithoutTracing(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_valueTransformerWithKey() {
    ValueTransformerWithKeySupplier<String, String, String> transformerSupplier =
      kafkaStreamsTracing.valueTransformerWithKey(
        "transformer-1", () ->
          new ValueTransformerWithKey<String, String, String>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public String transform(String key, String value) {
              try {
                Thread.sleep(100L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              return value;
            }

            @Override
            public void close() {
            }
          });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(transformerSupplier)
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreams(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(spanInput.tags()).containsEntry("kafka.topic", inputTopic);

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(spanOutput.tags()).containsEntry("kafka.topic", outputTopic);
    assertChildOf(spanOutput, spanProcessor);

    streams.close();
    streams.cleanUp();
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_valueTransformerWithKey() {
    ValueTransformerWithKeySupplier<String, String, String> transformerSupplier =
      kafkaStreamsTracing.valueTransformerWithKey(
        "transformer-1", () ->
          new ValueTransformerWithKey<String, String, String>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public String transform(String key, String value) {
              try {
                Thread.sleep(100L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              return value;
            }

            @Override
            public void close() {
            }
          });

    String inputTopic = testName.getMethodName() + "-input";
    String outputTopic = testName.getMethodName() + "-output";

    StreamsBuilder builder = new StreamsBuilder();
    builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
      .transformValues(transformerSupplier)
      .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
    Topology topology = builder.build();

    KafkaStreams streams = buildKafkaStreamsWithoutTracing(topology);

    send(new ProducerRecord<>(inputTopic, TEST_KEY, TEST_VALUE));

    waitForStreamToRun(streams);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

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
    } while (!streams.state().isRunning());
  }

  KafkaStreams buildKafkaStreams(Topology topology) {
    Properties properties = streamsProperties();
    return kafkaStreamsTracing.kafkaStreams(topology, properties);
  }

  Properties streamsProperties() {
    Properties properties = new Properties();
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafka.helper().consumerConfig().getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
    properties.put(StreamsConfig.STATE_DIR_CONFIG, "target/kafka-streams");
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, testName.getMethodName());
    properties.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
      Topology.AutoOffsetReset.EARLIEST.name().toLowerCase());
    return properties;
  }

  KafkaStreams buildKafkaStreamsWithoutTracing(Topology topology) {
    Properties properties = streamsProperties();
    return new KafkaStreams(topology, properties);
  }

  Producer<String, String> createProducer() {
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

  void send(ProducerRecord<String, String> record) {
    BlockingCallback callback = new BlockingCallback();
    producer.send(record, callback);
    callback.join();
  }
}

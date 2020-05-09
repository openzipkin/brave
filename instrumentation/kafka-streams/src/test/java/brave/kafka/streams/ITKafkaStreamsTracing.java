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
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import java.io.FileNotFoundException;
import java.util.*;

import static brave.kafka.streams.KafkaStreamsTags.KAFKA_STREAMS_FILTERED_TAG;
import static org.assertj.core.api.Assertions.assertThat;

public class ITKafkaStreamsTracing extends ITKafkaStreams {
  @ClassRule
  public static final KafkaJunitRule kafka = new KafkaJunitRule(EphemeralKafkaBroker.create());

  private final Consumed<String, String> stringConsumed = Consumed.with(Serdes.String(), Serdes.String());
  private final Produced<String, String> stringProduced = Produced.with(Serdes.String(), Serdes.String());
  Producer<String, String> producer = createProducer();
  KafkaStreams streams;

  @After
  public void close() {
    streams.close();
    streams.cleanUp();
    streams = null;
    producer.close();
  }

  @Test
  public void should_create_span_from_stream_input_topic() {
    Topology topology = givenStream(builder -> builder
      .foreach((k, v) -> {
      }));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());
  }

  @Test
  public void should_create_multiple_span_from_stream_input_topic_whenSharingDisabled() {
    Topology topology = givenStream(builder -> builder
      .foreach((k, v) -> {
      })
    );
    KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.newBuilder(tracing)
      .singleRootSpanOnReceiveBatch(false)
      .build();

    recordsReceived(3);
    start(kafkaStreamsTracing.kafkaStreams(topology, streamsProperties()));

    for (int i = 0; i < 3; i++) {
      Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
      hasTag(spanInput, "kafka.topic", inputTopic());
    }
  }

  @Test
  public void should_create_one_span_from_stream_input_topic_whenSharingEnabled() {
    Topology topology = givenStream(builder -> builder
      .foreach((k, v) -> {
      })
    );
    MessagingTracing messagingTracing = MessagingTracing.create(tracing);
    KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.newBuilder(messagingTracing)
      .singleRootSpanOnReceiveBatch(true)
      .build();

    recordsReceived(3);
    start(kafkaStreamsTracing.kafkaStreams(topology, streamsProperties()));

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());
  }

  @Test
  public void should_create_span_from_stream_input_topic_using_kafka_client_supplier() {
    Topology topology = givenStream(builder -> builder
      .foreach((k, v) -> {
      })
    );

    recordReceived();
    start(new KafkaStreams(topology, streamsProperties(), kafkaStreamsTracing.kafkaClientSupplier()));

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());
  }

  @Test
  public void should_create_spans_from_stream_input_and_output_topics() {
    Topology topology = givenStream(builder -> builder.to(outputTopic()));

    recordReceived();
    runStream(topology);

    Consumer<String, String> consumer = createTracingConsumer(outputTopic());

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanInput);
    consumer.close();
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_processor() {
    Topology topology = givenStream(builder -> builder
      .process(tracingProcessor()));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_predicate_true() {
    Topology topology = givenStream(builder ->
      builder
        .transform(kafkaStreamsTracing.filter("filter-1", (key, value) -> true))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_predicate_false() {
    Topology topology = givenStream(builder ->
      builder
        .transform(kafkaStreamsTracing.filter("filter-2", (key, value) -> false))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filter transformer returns false so record is dropped
  }

  @Test
  public void should_create_spans_and_propagate_extra_from_stream_with_multi_processor() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.peek("transform1", (o, o2) -> {
          TraceContext context = currentTraceContext.get();
          assertThat(BAGGAGE_FIELD.getValue(context)).isEqualTo("user1");
          BAGGAGE_FIELD.updateValue(context, "user2");
        }))
        .transformValues(kafkaStreamsTracing.peek("transform2", (s, s2) -> {
          TraceContext context = currentTraceContext.get();
          assertThat(BAGGAGE_FIELD.getValue(context)).isEqualTo("user2");
        }))
        .to(outputTopic(), stringProduced));

    ProducerRecord<String, String> record = new ProducerRecord<>(inputTopic(), TEST_KEY, TEST_VALUE);
    record.headers().add(BAGGAGE_FIELD_KEY, "user1".getBytes());
    send(record);
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanTransform1 = reporter.takeLocalSpan();
    assertChildOf(spanTransform1, spanInput);

    Span spanTransform2 = reporter.takeLocalSpan();
    assertChildOf(spanTransform2, spanTransform1);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanTransform2);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_not_predicate_true() {
    Topology topology = givenStream(builder ->
      builder
        .transform(kafkaStreamsTracing.filterNot("filterNot-1", (key, value) -> true))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filterNot transformer returns true so record is dropped
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_filter_not_predicate_false() {
    Topology topology = givenStream(builder ->
      builder
        .transform(kafkaStreamsTracing.filterNot("filterNot-2", (key, value) -> false))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_filtered_predicate_true() {
    Topology topology = givenStream(builder -> builder
      .transformValues(kafkaStreamsTracing.markAsFiltered("filter-1", (key, value) -> true))
      .filterNot((k, v) -> Objects.isNull(v))
      .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_filtered_predicate_false() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.markAsFiltered("filter-2", (key, value) -> false))
        .filterNot((k, v) -> Objects.isNull(v))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filter transformer returns false so record is dropped
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_not_filtered_predicate_true() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.markAsNotFiltered("filterNot-1", (key, value) -> true))
        .filterNot((k, v) -> Objects.isNull(v))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "true");

    // the filterNot transformer returns true so record is dropped
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark_as_not_filtered_predicate_false() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(
          kafkaStreamsTracing.markAsNotFiltered("filterNot-2", (key, value) -> false))
        .filterNot((k, v) -> Objects.isNull(v))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    hasTag(spanProcessor, KAFKA_STREAMS_FILTERED_TAG, "false");

    // the filter transformer returns true so record is not dropped

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_peek() {
    long now = System.currentTimeMillis();

    Topology topology = givenStream(builder -> builder
      .transformValues(kafkaStreamsTracing.peek("peek-1", (key, value) -> {
        doSomething();
        tracing.tracer().currentSpan().annotate(now, "test");
      }))
      .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.annotations()).contains(Annotation.create(now, "test"));

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mark() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.mark("mark-1"))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_foreach() {
    Topology topology = givenStream(builder ->
      builder
        .process(kafkaStreamsTracing.foreach("foreach-1", (key, value) -> {
          doSomething();
        })));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_and_tracing_processor() {
    Topology topology = givenStream(builder -> builder
      .process(tracingProcessor()));

    recordReceived();
    runKafkaStreamsWithoutTracing(topology);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_transformer() {
    Topology topology = givenStream(builder -> builder
      .transform(tracingTransformer())
      .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_throw_exception_upwards() {
    TransformerSupplier<String, String, KeyValue<String, String>> throwingTransformer =
      kafkaStreamsTracing.transformer(
        "exception-transformer", () ->
          new TestTransformer<KeyValue<String, String>>() {
            @Override
            public KeyValue<String, String> transform(String key, String value) {
              throw new IllegalArgumentException("illegal-argument");
            }
          });

    Topology topology = givenStream(builder ->
      builder
        .transform(throwingTransformer)
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpanWithError("illegal-argument");
    assertChildOf(spanProcessor, spanInput);

    assertThat(!streams.state().isRunning());
  }

  @Test
  public void should_throw_sneaky_exception_upwards() {
    TransformerSupplier<String, String, KeyValue<String, String>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "sneaky-exception-transformer", () ->
          new TestTransformer<KeyValue<String, String>>() {
            @Override
            public KeyValue<String, String> transform(String key, String value) {
              doThrowUnsafely(new FileNotFoundException("file-not-found"));
              return KeyValue.pair(key, value);
            }
          });

    Topology topology = givenStream(builder ->
      builder
        .transform(transformerSupplier)
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpanWithError("file-not-found");
    assertChildOf(spanProcessor, spanInput);

    assertThat(!streams.state().isRunning());
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_flattransformer() {
    TransformerSupplier<String, String, Iterable<KeyValue<String, String>>> transformerSupplier =
      kafkaStreamsTracing.transformer(
        "double-transformer-1", () ->
          new TestTransformer<Iterable<KeyValue<String, String>>>() {
            @Override
            public Iterable<KeyValue<String, String>> transform(String key, String value) {
              doSomething();
              return Arrays.asList(KeyValue.pair(key, value), KeyValue.pair(key, value));
            }
          });

    Topology topology = givenStream(builder ->
      builder
        .flatTransform(transformerSupplier)
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    for (int i = 0; i < 2; i++) {
      Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
      hasTag(spanOutput, "kafka.topic", outputTopic());
      assertChildOf(spanOutput, spanProcessor);
    }
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_transformer() {
    Topology topology = givenStream(builder ->
      builder
        .transform(tracingTransformer())
        .to(outputTopic(), stringProduced));

    recordReceived();
    runKafkaStreamsWithoutTracing(topology);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_valueTransformer() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(tracingValueTransformer())
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_map() {
    Topology topology = givenStream(builder ->
      builder
        .transform(kafkaStreamsTracing.map("map-1", (key, value) -> {
          doSomething();
          return KeyValue.pair(key, value);
        }))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_flatMap() {
    Topology topology = givenStream(builder ->
      builder
        .flatTransform(kafkaStreamsTracing.flatMap("flat-map-1", (key, value) -> {
          doSomething();
          return Arrays.asList(KeyValue.pair(key, value + "-1"), KeyValue.pair(key, value + "-2"));
        }))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);
    assertThat(spanProcessor.tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");

    for (int i = 0; i < 2; i++) {
      Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
      hasTag(spanOutput, "kafka.topic", outputTopic());
      assertChildOf(spanOutput, spanProcessor);
    }
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mapValues() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.mapValues("mapValue-1", this::valueMapper))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_mapValues_withKey() {

    Topology topology = givenStream(builder ->
      builder
        .transformValues(kafkaStreamsTracing.mapValues("mapValue-1", (key, value) -> valueMapper(value)))
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);

  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_valueTransformer() {

    Topology topology = givenStream(builder ->
      builder
        .transformValues(tracingValueTransformer())
        .to(outputTopic(), stringProduced));

    recordReceived();
    runKafkaStreamsWithoutTracing(topology);


    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");
  }

  @Test
  public void should_create_spans_from_stream_with_tracing_valueTransformerWithKey() {

    Topology topology = givenStream(builder ->
      builder
        .transformValues(someValueWithKeyTransformer())
        .to(outputTopic(), stringProduced));

    recordReceived();
    runStream(topology);

    Span spanInput = reporter.takeRemoteSpan(Span.Kind.CONSUMER);
    hasTag(spanInput, "kafka.topic", inputTopic());

    Span spanProcessor = reporter.takeLocalSpan();
    assertChildOf(spanProcessor, spanInput);

    Span spanOutput = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    hasTag(spanOutput, "kafka.topic", outputTopic());
    assertChildOf(spanOutput, spanProcessor);
  }

  @Test
  public void should_create_spans_from_stream_without_tracing_with_tracing_valueTransformerWithKey() {
    Topology topology = givenStream(builder ->
      builder
        .transformValues(someValueWithKeyTransformer())
        .to(outputTopic(), stringProduced));

    recordReceived();
    runKafkaStreamsWithoutTracing(topology);

    assertThat(reporter.takeLocalSpan().tags())
      .containsOnlyKeys("kafka.streams.application.id", "kafka.streams.task.id");
  }

  private TransformerSupplier<String, String, KeyValue<String, String>> tracingTransformer() {
    return kafkaStreamsTracing.transformer(
      "transformer-1", () ->
        new TestTransformer<KeyValue<String, String>>() {
          @Override
          public KeyValue<String, String> transform(String key, String value) {
            doSomething();
            return KeyValue.pair(key, value);
          }
        });
  }

  private ValueTransformerWithKeySupplier<String, String, String> someValueWithKeyTransformer() {
    return kafkaStreamsTracing.valueTransformerWithKey("transformer-1", this::valueTransformerWithKey);
  }
  // Armeria Black Magic copied from
  // https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/util/Exceptions.java#L197

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void doThrowUnsafely(Throwable cause) throws E {
    throw (E) cause;
  }

  private Topology givenStream(java.util.function.Consumer<KStream<String, String>> configurer) {
    StreamsBuilder streamsBuilder = new StreamsBuilder();
    KStream<String, String> stream = streamsBuilder
      .stream(inputTopic(), stringConsumed);
    configurer.accept(stream);
    return streamsBuilder.build();
  }

  private void recordsReceived(int count) {
    for (int i = 0; i < count; i++) {
      send(new ProducerRecord<>(inputTopic(), TEST_KEY, TEST_VALUE));
    }
  }

  private ValueTransformerSupplier<String, String> tracingValueTransformer() {
    return kafkaStreamsTracing.valueTransformer("transformer-1", this::valueTransformer);
  }

  private String inputTopic() {
    return testName.getMethodName() + "-input";
  }

  private String outputTopic() {
    return testName.getMethodName() + "-output";
  }

  private void doSomething() {
    try {
      Thread.sleep(100L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void start(KafkaStreams streams) {
    this.streams = streams;
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

  private ProcessorSupplier<String, String> tracingProcessor() {
    return kafkaStreamsTracing.processor("forward-1", () -> new AbstractProcessor<String, String>() {
      @Override
      public void process(String key, String value) {
        doSomething();
      }
    });
  }

  private ValueTransformerWithKey<String, String, String> valueTransformerWithKey() {
    return new ValueTransformerWithKey<String, String, String>() {
      ProcessorContext context;

      @Override
      public void init(ProcessorContext context) {
        this.context = context;
      }

      @Override
      public String transform(String key, String value) {
        return valueMapper(value);
      }

      @Override
      public void close() {
      }
    };
  }

  private void recordReceived() {
    recordsReceived(1);
  }

  private String valueMapper(String value) {
    doSomething();
    return value;
  }

  private ValueTransformer<String, String> valueTransformer() {
    return new ValueTransformer<String, String>() {
      ProcessorContext context;

      @Override
      public void init(ProcessorContext context) {
        this.context = context;
      }

      @Override
      public String transform(String value) {
        return valueMapper(value);
      }

      @Override
      public void close() {
      }
    };
  }

  private void runStream(Topology topology) {
    KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsProperties());
    start(kafkaStreams);
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

  void runKafkaStreamsWithoutTracing(Topology topology) {
    KafkaStreams kafkaStreams = new KafkaStreams(topology, streamsProperties());
    start(kafkaStreams);
  }

  Producer<String, String> createProducer() {
    return kafka.helper().createStringProducer();
  }

  private void hasTag(Span spanInput, String tagName, String tagValue) {
    assertThat(spanInput.tags()).containsEntry(tagName, tagValue);
  }

  Consumer<String, String> createTracingConsumer(String... topics) {
    if (topics.length == 0) {
      topics = new String[]{testName.getMethodName()};
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

/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Properties;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.apache.kafka.streams.kstream.ValueMapperWithKey;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

/** Use this class to decorate Kafka Stream Topologies and enable Tracing. */
public final class KafkaStreamsTracing {

  final Tracing tracing;
  final TraceContext.Extractor<Headers> extractor;
  final TraceContext.Injector<Headers> injector;

  KafkaStreamsTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaStreamsPropagation.GETTER);
    this.injector = tracing.propagation().injector(KafkaStreamsPropagation.SETTER);
  }

  public static KafkaStreamsTracing create(Tracing tracing) {
    return new KafkaStreamsTracing.Builder(tracing).build();
  }

  /**
   * Provides a {@link KafkaClientSupplier} with tracing enabled, hence Producer and Consumer
   * operations will be traced.
   *
   * This is mean to be used in scenarios {@link KafkaStreams} creation is not controlled by the
   * user but framework (e.g. Spring Kafka Streams) creates it, and {@link KafkaClientSupplier} is
   * accepted.
   */
  public KafkaClientSupplier kafkaClientSupplier() {
    final KafkaTracing kafkaTracing = KafkaTracing.create(tracing);
    return new TracingKafkaClientSupplier(kafkaTracing);
  }

  /**
   * Creates a {@link KafkaStreams} instance with a tracing-enabled {@link KafkaClientSupplier}. All
   * Topology Sources and Sinks (including internal Topics) will create Spans on records processed
   * (i.e. send or consumed).
   *
   * Use this instead of {@link KafkaStreams} constructor.
   *
   * <p>Simple example:
   * <pre>{@code
   * // KafkaStreams with tracing-enabled KafkaClientSupplier
   * KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public KafkaStreams kafkaStreams(Topology topology, Properties streamsConfig) {
    return new KafkaStreams(topology, streamsConfig, kafkaClientSupplier());
  }

  /**
   * Create a tracing-decorated {@link ProcessorSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .process(kafkaStreamsTracing.processor("my-processor", myProcessor);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public <K, V> ProcessorSupplier<K, V> processor(String spanName, Processor<K, V> processor) {
    return new TracingProcessorSupplier<>(this, spanName, processor);
  }

  /**
   * Create a tracing-decorated {@link TransformerSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.transformer("my-transformer", myTransformer)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, R> TransformerSupplier<K, V, R> transformer(String spanName,
    Transformer<K, V, R> transformer) {
    return new TracingTransformerSupplier<>(this, spanName, transformer);
  }

  /**
   * Create a tracing-decorated {@link ValueTransformerSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.valueTransformer("my-transformer", myTransformer)
   *        .to(outputTopic);
   * }</pre>
   */
  public <V, VR> ValueTransformerSupplier<V, VR> valueTransformer(String spanName,
    ValueTransformer<V, VR> valueTransformer) {
    return new TracingValueTransformerSupplier<>(this, spanName, valueTransformer);
  }

  /**
   * Create a tracing-decorated {@link ValueTransformerWithKeySupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.valueTransformerWithKey("my-transformer", myTransformer)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, VR> ValueTransformerWithKeySupplier<K, V, VR> valueTransformerWithKey(
    String spanName,
    ValueTransformerWithKey<K, V, VR> valueTransformerWithKey) {
    return new TracingValueTransformerWithKeySupplier<>(this, spanName, valueTransformerWithKey);
  }

  /**
   * Create a foreach processor, similar to {@link KStream#foreach(ForeachAction)}, where its action
   * will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .process(kafkaStreamsTracing.foreach("myForeach", (k, v) -> ...);
   * }</pre>
   */
  public <K, V> ProcessorSupplier<K, V> foreach(String spanName, ForeachAction<K, V> action) {
    return new TracingProcessorSupplier<>(this, spanName, new AbstractProcessor<K, V>() {
      @Override public void process(K key, V value) {
        action.apply(key, value);
      }
    });
  }

  /**
   * Create a peek transformer, similar to {@link KStream#peek(ForeachAction)}, where its action
   * will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.peek("myPeek", (k, v) -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V> ValueTransformerWithKeySupplier<K, V, V> peek(String spanName,
    ForeachAction<K, V> action) {
    return new TracingValueTransformerWithKeySupplier<>(this, spanName,
      new AbstractTracingValueTransformerWithKey<K, V, V>() {
        @Override public V transform(K key, V value) {
          action.apply(key, value);
          return value;
        }
      });
  }

  /**
   * Create a mark transformer, similar to {@link KStream#peek(ForeachAction)}, but no action is
   * executed. Instead, only a span is created to represent an event as part of the stream process.
   *
   * A common scenario for this transformer is to mark the beginning and end of a step (or set of
   * steps) in a stream process.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.mark("beginning-complex-map")
   *        .map(complexTransformation1)
   *        .filter(predicate)
   *        .mapValues(complexTransformation2)
   *        .transform(kafkaStreamsTracing.mark("end-complex-transformation")
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V> ValueTransformerWithKeySupplier<K, V, V> mark(String spanName) {
    return new TracingValueTransformerWithKeySupplier<>(this, spanName,
      new AbstractTracingValueTransformerWithKey<K, V, V>() {
        @Override public V transform(K key, V value) {
          return value;
        }
      });
  }

  /**
   * Create a map transformer, similar to {@link KStream#map(KeyValueMapper)}, where its mapper
   * action will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.map("myMap", (k, v) -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, KR, VR> TransformerSupplier<K, V, KeyValue<KR, VR>> map(String spanName,
    KeyValueMapper<K, V, KeyValue<KR, VR>> mapper) {
    return new TracingTransformerSupplier<>(this, spanName,
      new AbstractTracingTransformer<K, V, KeyValue<KR, VR>>() {
        @Override public KeyValue<KR, VR> transform(K key, V value) {
          return mapper.apply(key, value);
        }
      });
  }

  /**
   * Create a filter transformer.
   *
   * WARNING: this filter implementation uses the Streams transform API, meaning that
   * re-partitioning can occur if a key modifying operation like grouping or joining operation is
   * applied after this filter.
   *
   * In that case, consider using {@link #markAsFiltered(String, Predicate)} instead which uses
   * {@link ValueTransformerWithKey} API instead.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *       .transform(kafkaStreamsTracing.filter("myFilter", (k, v) -> ...)
   *       .to(outputTopic);
   * }</pre>
   */
  public <K, V> TransformerSupplier<K, V, KeyValue<K, V>> filter(String spanName,
    Predicate<K, V> predicate) {
    return new TracingFilterTransformerSupplier<>(this, spanName, predicate, false);
  }

  /**
   * Create a filterNot transformer.
   *
   * WARNING: this filter implementation uses the Streams transform API, meaning that
   * re-partitioning can occur if a key modifying operation like grouping or joining operation is
   * applied after this filter. In that case, consider using {@link #markAsNotFiltered(String,
   * Predicate)} instead which uses {@link ValueTransformerWithKey} API instead.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *       .transform(kafkaStreamsTracing.filterNot("myFilter", (k, v) -> ...)
   *       .to(outputTopic);
   * }</pre>
   */
  public <K, V> TransformerSupplier<K, V, KeyValue<K, V>> filterNot(String spanName,
    Predicate<K, V> predicate) {
    return new TracingFilterTransformerSupplier<>(this, spanName, predicate, true);
  }

  /**
   * Create a markAsFiltered valueTransformer.
   *
   * Instead of filtering, and not emitting values downstream as {@code filter} does; {@code
   * markAsFiltered} creates a span, marking it as filtered or not. If filtered, value returned will
   * be {@code null} and will require an additional non-null value filter to complete the
   * filtering.
   *
   * This operation is offered as lack of a processor that allows to continue conditionally with the
   * processing without risk of accidental re-partitioning.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *       .transformValues(kafkaStreamsTracing.markAsFiltered("myFilter", (k, v) -> ...)
   *       .filterNot((k, v) -> Objects.isNull(v))
   *       .to(outputTopic);
   * }</pre>
   */
  public <K, V> ValueTransformerWithKeySupplier<K, V, V> markAsFiltered(String spanName,
    Predicate<K, V> predicate) {
    return new TracingFilterValueTransformerWithKeySupplier<>(this, spanName, predicate, false);
  }

  /**
   * Create a markAsNotFiltered valueTransformer.
   *
   * Instead of filtering, and not emitting values downstream as {@code filterNot} does; {@code
   * markAsNotFiltered} creates a span, marking it as filtered or not. If filtered, value returned
   * will be {@code null} and will require an additional non-null value filter to complete the
   * filtering.
   *
   * This operation is offered as lack of a processor that allows to continue conditionally with the
   * processing without risk of accidental re-partitioning.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *       .transformValues(kafkaStreamsTracing.markAsNotFiltered("myFilter", (k, v) -> ...)
   *       .filterNot((k, v) -> Objects.isNull(v))
   *       .to(outputTopic);
   * }</pre>
   */
  public <K, V> ValueTransformerWithKeySupplier<K, V, V> markAsNotFiltered(String spanName,
    Predicate<K, V> predicate) {
    return new TracingFilterValueTransformerWithKeySupplier<>(this, spanName, predicate, true);
  }

  /**
   * Create a mapValues transformer, similar to {@link KStream#mapValues(ValueMapperWithKey)}, where
   * its mapper action will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.mapValues("myMapValues", (k, v) -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, VR> ValueTransformerWithKeySupplier<K, V, VR> mapValues(String spanName,
    ValueMapperWithKey<K, V, VR> mapper) {
    return new TracingValueTransformerWithKeySupplier<>(this, spanName,
      new AbstractTracingValueTransformerWithKey<K, V, VR>() {
        @Override public VR transform(K readOnlyKey, V value) {
          return mapper.apply(readOnlyKey, value);
        }
      });
  }

  /**
   * Create a mapValues transformer, similar to {@link KStream#mapValues(ValueMapper)}, where its
   * mapper action will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.mapValues("myMapValues", v -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <V, VR> ValueTransformerSupplier<V, VR> mapValues(String spanName,
    ValueMapper<V, VR> mapper) {
    return new TracingValueTransformerSupplier<>(this, spanName,
      new AbstractTracingValueTransformer<V, VR>() {
        @Override public VR transform(V value) {
          return mapper.apply(value);
        }
      });
  }

  static void addTags(ProcessorContext processorContext, SpanCustomizer result) {
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_APPLICATION_ID_TAG, processorContext.applicationId());
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_TASK_ID_TAG, processorContext.taskId().toString());
  }

  Span nextSpan(ProcessorContext context) {
    TraceContextOrSamplingFlags extracted = extractor.extract(context.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    if (!result.isNoop()) {
      addTags(context, result);
    }
    return result;
  }

  public static final class Builder {
    final Tracing tracing;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public KafkaStreamsTracing build() {
      return new KafkaStreamsTracing(this);
    }
  }
}

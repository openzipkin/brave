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

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.apache.kafka.streams.kstream.ValueMapperWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

/** Use this class to decorate Kafka Stream Topologies and enable Tracing. */
public final class KafkaStreamsTracing {
  final KafkaTracing kafkaTracing;
  final Tracer tracer;
  final Extractor<Headers> extractor;
  final Injector<Headers> injector;
  final Set<String> propagationKeys;
  final TraceContextOrSamplingFlags emptyExtraction;

  KafkaStreamsTracing(Builder builder) { // intentionally hidden constructor
    this.kafkaTracing = builder.kafkaTracing.toBuilder()
      .singleRootSpanOnReceiveBatch(builder.singleRootSpanOnReceiveBatch)
      .build();
    this.tracer = kafkaTracing.messagingTracing().tracing().tracer();
    Propagation<String> propagation = kafkaTracing.messagingTracing().propagation();
    this.extractor = propagation.extractor(KafkaStreamsPropagation.GETTER);
    this.injector = propagation.injector(KafkaStreamsPropagation.SETTER);
    this.propagationKeys = new LinkedHashSet<>(propagation.keys());
    // When Baggage or similar are in use, the result != TraceContextOrSamplingFlags.EMPTY
    this.emptyExtraction = propagation.extractor((c, k) -> null).extract(Boolean.TRUE);
  }

  public static KafkaStreamsTracing create(Tracing tracing) {
    return create(KafkaTracing.create(tracing));
  }

  /** @since 5.10 */
  public static KafkaStreamsTracing create(MessagingTracing messagingTracing) {
    return new Builder(KafkaTracing.create(messagingTracing)).build();
  }

  /** @since 5.9 */
  public static KafkaStreamsTracing create(KafkaTracing kafkaTracing) {
    return new Builder(kafkaTracing).build();
  }

  /** @since 5.10 */
  public static Builder newBuilder(Tracing tracing) {
    return new Builder(KafkaTracing.create(tracing));
  }

  /** @since 5.10 */
  public static Builder newBuilder(MessagingTracing messagingTracing) {
    return new Builder(KafkaTracing.create(messagingTracing));
  }

  /**
   * Provides a {@link KafkaClientSupplier} with tracing enabled, hence Producer and Consumer
   * operations will be traced.
   * <p>
   * This is mean to be used in scenarios {@link KafkaStreams} creation is not controlled by the
   * user but framework (e.g. Spring Kafka Streams) creates it, and {@link KafkaClientSupplier} is
   * accepted.
   */
  public KafkaClientSupplier kafkaClientSupplier() {
    return new TracingKafkaClientSupplier(kafkaTracing);
  }

  /**
   * Creates a {@link KafkaStreams} instance with a tracing-enabled {@link KafkaClientSupplier}. All
   * Topology Sources and Sinks (including internal Topics) will create Spans on records processed
   * (i.e. send or consumed).
   * <p>
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
   *        .process(kafkaStreamsTracing.processor("my-processor", myProcessorSupplier);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public <K, V> ProcessorSupplier<K, V> processor(String spanName,
    ProcessorSupplier<K, V> processorSupplier) {
    return new TracingProcessorSupplier<>(this, spanName, processorSupplier);
  }

  /**
   * Create a tracing-decorated {@link TransformerSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.transformer("my-transformer", myTransformerSupplier)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, R> TransformerSupplier<K, V, R> transformer(String spanName,
    TransformerSupplier<K, V, R> transformerSupplier) {
    return new TracingTransformerSupplier<>(this, spanName, transformerSupplier);
  }

  /**
   * Create a tracing-decorated {@link ValueTransformerSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.valueTransformer("my-transformer", myTransformerSupplier)
   *        .to(outputTopic);
   * }</pre>
   */
  public <V, VR> ValueTransformerSupplier<V, VR> valueTransformer(String spanName,
    ValueTransformerSupplier<V, VR> valueTransformerSupplier) {
    return new TracingValueTransformerSupplier<>(this, spanName, valueTransformerSupplier);
  }

  /**
   * Create a tracing-decorated {@link ValueTransformerWithKeySupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transformValues(kafkaStreamsTracing.valueTransformerWithKey("my-transformer", myTransformerSupplier)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, VR> ValueTransformerWithKeySupplier<K, V, VR> valueTransformerWithKey(
    String spanName,
    ValueTransformerWithKeySupplier<K, V, VR> valueTransformerWithKeySupplier) {
    return new TracingValueTransformerWithKeySupplier<>(this, spanName,
      valueTransformerWithKeySupplier);
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
    return new TracingProcessorSupplier<>(this, spanName, () ->
      new AbstractProcessor<K, V>() {
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
    return new TracingValueTransformerWithKeySupplier<>(this, spanName, () ->
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
   * <p>
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
    return new TracingValueTransformerWithKeySupplier<>(this, spanName, () ->
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
    return new TracingTransformerSupplier<>(this, spanName, () ->
      new AbstractTracingTransformer<K, V, KeyValue<KR, VR>>() {
        @Override public KeyValue<KR, VR> transform(K key, V value) {
          return mapper.apply(key, value);
        }
      });
  }

  /**
   * Create a flatMap transformer, similar to {@link KStream#flatMap(KeyValueMapper)}, where its
   * mapper action will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .flatTransform(kafkaStreamsTracing.flatMap("myflatMap", (k, v) -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V, KR, VR> TransformerSupplier<K, V, Iterable<KeyValue<KR, VR>>> flatMap(
    String spanName,
    KeyValueMapper<K, V, Iterable<KeyValue<KR, VR>>> mapper) {
    return new TracingTransformerSupplier<>(this, spanName, () ->
      new AbstractTracingTransformer<K, V, Iterable<KeyValue<KR, VR>>>() {
        @Override public Iterable<KeyValue<KR, VR>> transform(K key, V value) {
          return mapper.apply(key, value);
        }
      });
  }

  /**
   * Create a filter transformer.
   * <p>
   * WARNING: this filter implementation uses the Streams transform API, meaning that
   * re-partitioning can occur if a key modifying operation like grouping or joining operation is
   * applied after this filter.
   * <p>
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
   * <p>
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
   * <p>
   * Instead of filtering, and not emitting values downstream as {@code filter} does; {@code
   * markAsFiltered} creates a span, marking it as filtered or not. If filtered, value returned will
   * be {@code null} and will require an additional non-null value filter to complete the
   * filtering.
   * <p>
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
   * <p>
   * Instead of filtering, and not emitting values downstream as {@code filterNot} does; {@code
   * markAsNotFiltered} creates a span, marking it as filtered or not. If filtered, value returned
   * will be {@code null} and will require an additional non-null value filter to complete the
   * filtering.
   * <p>
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
    return new TracingValueTransformerWithKeySupplier<>(this, spanName, () ->
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
    return new TracingValueTransformerSupplier<>(this, spanName, () ->
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
    // Clear any propagation keys present in the headers
    if (!extracted.equals(emptyExtraction)) {
      clearHeaders(context.headers());
    }
    Span result = tracer.nextSpan(extracted);
    if (!result.isNoop()) {
      addTags(context, result);
    }
    return result;
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or visa versa.
  void clearHeaders(Headers headers) {
    // Headers::remove creates and consumes an iterator each time. This does one loop instead.
    for (Iterator<Header> i = headers.iterator(); i.hasNext(); ) {
      Header next = i.next();
      if (propagationKeys.contains(next.key())) i.remove();
    }
  }

  public static final class Builder {
    final KafkaTracing kafkaTracing;
    boolean singleRootSpanOnReceiveBatch = false;

    Builder(KafkaTracing kafkaTracing) {
      if (kafkaTracing == null) throw new NullPointerException("kafkaTracing == null");
      this.kafkaTracing = kafkaTracing;
    }

    /**
     * Controls the sharing of a {@code poll} span for incoming spans with no trace context.
     *
     * <p>If true, all the spans received in a poll batch that do not have trace-context will be
     * added to a single new {@code poll} root span. Otherwise, a {@code poll} span will be created
     * for each such message.
     *
     * @since 5.10
     */
    public Builder singleRootSpanOnReceiveBatch(boolean singleRootSpanOnReceiveBatch) {
      this.singleRootSpanOnReceiveBatch = singleRootSpanOnReceiveBatch;
      return this;
    }

    public KafkaStreamsTracing build() {
      return new KafkaStreamsTracing(this);
    }
  }
}

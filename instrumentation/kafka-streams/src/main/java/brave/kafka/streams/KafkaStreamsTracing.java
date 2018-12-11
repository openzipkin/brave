package brave.kafka.streams;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Properties;
import java.util.function.BiConsumer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
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

  KafkaStreamsTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaStreamsPropagation.GETTER);
  }

  public static KafkaStreamsTracing create(Tracing tracing) {
    return new KafkaStreamsTracing.Builder(tracing).build();
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
    final KafkaTracing kafkaTracing = KafkaTracing.create(tracing);
    final KafkaClientSupplier kafkaClientSupplier = new TracingKafkaClientSupplier(kafkaTracing);
    return new KafkaStreams(topology, streamsConfig, kafkaClientSupplier);
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
  public <K, V, VR> ValueTransformerWithKeySupplier<K, V, VR> valueTransformerWithKey(String spanName,
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
   *        .transform(kafkaStreamsTracing.peek("myPeek", (k, v) -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V> TransformerSupplier<K, V, KeyValue<K, V>> peek(String spanName, ForeachAction<K, V> action) {
    return new TracingTransformerSupplier<>(this, spanName, new AbstractTracingTransformer<K, V, KeyValue<K, V>>() {
      @Override public KeyValue<K, V> transform(K key, V value) {
        action.apply(key, value);
        return KeyValue.pair(key, value);
      }
    });
  }

  /**
   * Create a mark transformer, similar to {@link KStream#peek(ForeachAction)}, but no action is executed.
   * Instead, only a span is created to represent an event as part of the stream process.
   *
   * A common scenario for this transformer is to mark the beginning and end of a step (or set of steps)
   * in a stream process.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.mark("beginning-complex-map")
   *        .map(complexTransformation1)
   *        .filter(predicate)
   *        .map(complexTransformation2)
   *        .transform(kafkaStreamsTracing.mark("end-complex-transformation")
   *        .to(outputTopic);
   * }</pre>
   */
  public <K, V> TransformerSupplier<K, V, KeyValue<K, V>> mark(String spanName) {
    return new TracingTransformerSupplier<>(this, spanName, new AbstractTracingTransformer<K, V, KeyValue<K, V>>() {
      @Override public KeyValue<K, V> transform(K key, V value) {
        return KeyValue.pair(key, value);
      }
    });
  }

  /**
   * Create a map transformer, similar to {@link KStream#map(KeyValueMapper)}, where its mapper action
   * will be recorded in a new span with the indicated name.
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
   * Create a peek transformer, similar to {@link KStream#mapValues(ValueMapperWithKey)}, where its mapper action
   * will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.mapValues("myMapValues", (k, v) -> ...)
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
   * Create a peek transformer, similar to {@link KStream#mapValues(ValueMapper)}, where its mapper action
   * will be recorded in a new span with the indicated name.
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .transform(kafkaStreamsTracing.mapValues("myMapValues", v -> ...)
   *        .to(outputTopic);
   * }</pre>
   */
  public <V, VR> ValueTransformerSupplier<V, VR> mapValues(String spanName, ValueMapper<V, VR> mapper) {
    return new TracingValueTransformerSupplier<>(this, spanName, new AbstractTracingValueTransformer<V, VR>() {
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

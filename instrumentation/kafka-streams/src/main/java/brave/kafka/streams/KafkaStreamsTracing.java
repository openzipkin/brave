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
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
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
   * Topology Sources and Sinks (including internal Topics) will create Spans on records
   * processed (i.e. send or consumed).
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
  public <K, V> ProcessorSupplier<K, V> processor(String name, Processor<K, V> processor) {
    return new TracingProcessorSupplier<>(this, name, processor);
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
  public <K, V, R> TransformerSupplier<K, V, R> transformer(String name,
      Transformer<K, V, R> transformer) {
    return new TracingTransformerSupplier<>(this, name, transformer);
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
  public <V, VR> ValueTransformerSupplier<V, VR> valueTransformer(String name,
      ValueTransformer<V, VR> valueTransformer) {
    return new TracingValueTransformerSupplier<>(this, name, valueTransformer);
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
  public <K, V, VR> ValueTransformerWithKeySupplier<K, V, VR> valueTransformerWithKey(String name,
      ValueTransformerWithKey<K, V, VR> valueTransformerWithKey) {
    return new TracingValueTransformerWithKeySupplier<>(this, name, valueTransformerWithKey);
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

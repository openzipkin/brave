package brave.kafka.streams;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

import java.util.Properties;

/** Use this class to decorate Kafka Stream Topologies and enable Tracing. */
public final class KafkaStreamsTracing {

  public static KafkaStreamsTracing create(Tracing tracing) {
    return new KafkaStreamsTracing.Builder(tracing).build();
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

  final Tracing tracing;
  final TraceContext.Extractor<Headers> extractor;

  KafkaStreamsTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaStreamsPropagation.GETTER);
  }

  /**
   * Creates a {@link KafkaStreams} instance with a tracing-enabled {@link KafkaClientSupplier}.
   * All Topology Sources and Sinks (including internal Topics) will create a Spans on records processed.
   */
  public KafkaStreams kafkaStreams(Topology topology, Properties streamsConfig) {
    return new KafkaStreams(topology, streamsConfig, kafkaClientSupplier());
  }

  /**
   * Create a tracing-decorated {@link ProcessorSupplier}
   */
  public <K, V> ProcessorSupplier<K, V> processorSupplier(String name, Processor<K, V> processor) {
    return new TracingProcessorSupplier<>(this, name, processor);
  }

  /**
   * Create a tracing-decorated {@link TransformerSupplier}
   */
  public <K, V, R>TransformerSupplier<K, V, R> transformerSupplier(String name, Transformer<K, V, R> transformer) {
    return new TracingTransformerSupplier<>(this, name, transformer);
  }

  /** Returns a client supplier which traces send and receive operations. */
  KafkaClientSupplier kafkaClientSupplier() {
    KafkaTracing kafkaTracing = KafkaTracing.create(tracing);
    return new TracingKafkaClientSupplier(kafkaTracing);
  }

  Span nextSpan(ProcessorContext context) {
    TraceContextOrSamplingFlags extracted = extractor.extract(context.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    if (!result.isNoop()) {
      addTags(context, result);
    }
    return result;
  }

  static void addTags(ProcessorContext processorContext, SpanCustomizer result) {
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_APPLICATION_ID_TAG, processorContext.applicationId());
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_TASK_ID_TAG, processorContext.taskId().toString());
  }
}

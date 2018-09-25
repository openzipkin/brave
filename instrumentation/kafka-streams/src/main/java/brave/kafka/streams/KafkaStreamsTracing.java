package brave.kafka.streams;

import brave.Span;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

/** Use this class to decorate your Kafka Streams client. */
public final class KafkaStreamsTracing {

  public static KafkaStreamsTracing create(Tracing tracing) {
    return new KafkaStreamsTracing(tracing);
  }

  final Tracing tracing;
  final KafkaTracing kafkaTracing;
  final TraceContext.Extractor<Headers> extractor;

  KafkaStreamsTracing(Tracing tracing) { // intentionally hidden constructor
    this.tracing = tracing;
    this.extractor = tracing.propagation().extractor(KafkaStreamsPropagation.GETTER);
    this.kafkaTracing = KafkaTracing.create(tracing);
  }


  public Span nextSpan(ProcessorContext context) {
    TraceContextOrSamplingFlags extracted = extractor.extract(context.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    return result;
  }

  public <K, V> ProcessorSupplier<K, V> processorSupplier(String name, Processor<K, V> processor) {
    return new TracingProcessorSupplier<>(this, name, processor);
  }

  public <K, V, R>TransformerSupplier<K, V, R> transformerSupplier(String name, Transformer<K, V, R> transformer) {
    return new TracingTransformerSupplier<>(this, name, transformer);
  }

  /** Returns a client supplier which traces send and receive operations. */
  public KafkaClientSupplier kafkaClientSupplier() {
    return new TracingKafkaClientSupplier(kafkaTracing);
  }
}

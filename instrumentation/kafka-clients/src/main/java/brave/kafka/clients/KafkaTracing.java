package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;

/**
 * Use this class to decorate your Kafka consumer / producer and enable Tracing.
 */
public final class KafkaTracing {

  public static KafkaTracing create(Tracing tracing) {
    return new KafkaTracing(tracing);
  }

  private final Tracing tracing;
  private final TraceContext.Extractor<ConsumerRecord> extractor;

  KafkaTracing(Tracing tracing) { // hidden constructor
    if (tracing == null) {
      throw new NullPointerException("tracing == null");
    }
    this.tracing = tracing;
    this.extractor = tracing.propagation().extractor(new KafkaPropagation.ConsumerRecordGetter());
  }

  public <K, V> Consumer<K, V> consumer(Consumer<K, V> consumer) {
    return new TracingConsumer<>(tracing, consumer);
  }

  public <K, V> Producer<K, V> producer(Producer<K, V> producer) {
    return new TracingProducer<>(tracing, producer);
  }

  /**
   * Retrieve the span extracted from the record headers. Creates a root span if the context is not
   * available.
   */
  public Span nextSpan(ConsumerRecord record) {
    TraceContextOrSamplingFlags contextOrSamplingFlags = extractor.extract(record);
    if (contextOrSamplingFlags.context() != null) {
      return tracing.tracer().toSpan(contextOrSamplingFlags.context());
    } else {
        return tracing.tracer().newTrace(contextOrSamplingFlags.samplingFlags());
    }
  }
}

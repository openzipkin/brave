package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;

/**
 * Use this class to decorate your Kafka consumer / producer and enable Tracing.
 */
public final class KafkaTracing {

  private final KafkaPropagation.ConsumerExtractor extractor =
      new KafkaPropagation.ConsumerExtractor();

  public static KafkaTracing create(Tracing tracing) {
    return new KafkaTracing(tracing);
  }

  private final Tracing tracing;

  KafkaTracing(Tracing tracing) { // hidden constructor
    if (tracing == null) {
      throw new NullPointerException("tracing == null");
    }
    this.tracing = tracing;
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
    TraceContext context = tracing.propagation()
        .extractor(extractor)
        .extract(record).context();
    if (context != null) {
      return tracing.tracer().toSpan(context);
    } else {
      return tracing.tracer().newTrace();
    }
  }
}

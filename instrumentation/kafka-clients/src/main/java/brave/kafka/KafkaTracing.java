package brave.kafka;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import zipkin.internal.Util;

/**
 * Use this class to decorate your Kafka consumer / producer and enable Tracing.
 */
public final class KafkaTracing {

  public static KafkaTracing create(Tracing tracing) {
    return new KafkaTracing(tracing);
  }

  final Tracing tracing;

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
   * Continues the trace extracted from the headers or creates a new one if the extract fails. Call
   * this method while consumming your kafka records.
   */
  public Span nextSpan(ConsumerRecord record) {
    TraceContext.Extractor<Headers> extractor = tracing.propagation().extractor(
        (carrier, key) -> {
          Header header = carrier.lastHeader(key);
          if (header == null) return null;
          return new String(header.value(), Util.UTF_8);
        });
    TraceContext context = extractor.extract(record.headers()).context();
    if (context != null) {
      return tracing.tracer().newChild(context);
    } else {
      return tracing.tracer().nextSpan();
    }
  }
}

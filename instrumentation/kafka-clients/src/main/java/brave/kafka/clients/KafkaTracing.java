package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Headers;

/** Use this class to decorate your Kafka consumer / producer and enable Tracing. */
public final class KafkaTracing {

  public static KafkaTracing create(Tracing tracing) {
    return new KafkaTracing(tracing);
  }

  private final Tracing tracing;
  private final TraceContext.Extractor<Headers> extractor;

  KafkaTracing(Tracing tracing) { // hidden constructor
    if (tracing == null) throw new NullPointerException("tracing == null");
    this.tracing = tracing;
    this.extractor = tracing.propagation().extractor(KafkaPropagation.HEADER_GETTER);
  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. A child span
   * is injected onto each message for a later processor to use. It can be read later with {@link
   * #joinSpan(ConsumerRecord)}.
   */
  public <K, V> Consumer<K, V> consumer(Consumer<K, V> consumer) {
    return new TracingConsumer<>(tracing, consumer);
  }

  /** Starts and propagates {@link Span.Kind#PRODUCER} span for each message sent. */
  public <K, V> Producer<K, V> producer(Producer<K, V> producer) {
    return new TracingProducer<>(tracing, producer);
  }

  /**
   * Retrieve the span extracted from the record headers. Creates a root span if the context is not
   * available.
   */
  public Span joinSpan(ConsumerRecord record) {
    TraceContextOrSamplingFlags extracted = extractor.extract(record.headers());
    if (extracted.context() != null) {
      return tracing.tracer().toSpan(extracted.context()); // avoid creating an unnecessary child
    } else if (extracted.traceIdContext() != null) {
      return tracing.tracer().newTrace(extracted.traceIdContext());
    } else {
      return tracing.tracer().newTrace(extracted.samplingFlags());
    }
  }

  static void finish(Span span, @Nullable Throwable error) {
    if (error != null) { // an error occurred, adding error to span
      String message = error.getMessage();
      if (message == null) message = error.getClass().getSimpleName();
      span.tag("error", message);
    }
    span.finish();
  }
}

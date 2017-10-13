package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
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
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link
   * #nextSpan(ConsumerRecord)}.
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
   *
   * @deprecated this results in appending to a span already complete. Please use {@link
   * #nextSpan(ConsumerRecord)}
   */
  @Deprecated
  public Span joinSpan(ConsumerRecord<?, ?> record) {
    TraceContextOrSamplingFlags extracted = extractor.extract(record.headers());
    if (extracted.context() != null) {
      return tracing.tracer().toSpan(extracted.context()); // avoid creating an unnecessary child
    }
    Span result = tracing.tracer().nextSpan(extracted);
    if (!result.isNoop()) addTags(record, result);
    return result;
  }

  /**
   * Use this to create a span for processing the given record. Note: the result has no name and is
   * not started.
   *
   * <p>This creates a child from identifiers extracted from the record headers, or a new span if
   * one couldn't be extracted.
   */
  public Span nextSpan(ConsumerRecord<?, ?> record) {
    TraceContextOrSamplingFlags extracted = extractor.extract(record.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    if (extracted.context() == null && !result.isNoop()) {
      addTags(record, result);
    }
    return result;
  }

  /** When an upstream context was not present, lookup keys are unlikely added */
  static void addTags(ConsumerRecord<?, ?> record, Span result) {
    if (record.key() instanceof String && !"".equals(record.key())) {
      result.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
    }
    result.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());
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

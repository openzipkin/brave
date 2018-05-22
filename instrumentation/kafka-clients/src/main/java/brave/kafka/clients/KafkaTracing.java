package brave.kafka.clients;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Headers;

/** Use this class to decorate your Kafka consumer / producer and enable Tracing. */
public final class KafkaTracing {

  public static KafkaTracing create(Tracing tracing) {
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    String remoteServiceName = "kafka";

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    /**
     * The remote service name that describes the broker in the dependency graph. Defaults to
     * "kafka"
     */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public KafkaTracing build() {
      return new KafkaTracing(this);
    }
  }

  private final Tracing tracing;
  private final TraceContext.Extractor<Headers> extractor;
  private final String remoteServiceName;

  KafkaTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaPropagation.HEADER_GETTER);
    this.remoteServiceName = builder.remoteServiceName;
  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link
   * #nextSpan(ConsumerRecord)}.
   */
  public <K, V> Consumer<K, V> consumer(Consumer<K, V> consumer) {
    return new TracingConsumer<>(tracing, consumer, remoteServiceName);
  }

  /** Starts and propagates {@link Span.Kind#PRODUCER} span for each message sent. */
  public <K, V> Producer<K, V> producer(Producer<K, V> producer) {
    return new TracingProducer<>(tracing, producer, remoteServiceName);
  }

  /**
   * Use this to create a span for processing the given record. Note: the result has no name and is
   * not started.
   *
   * <p>This creates a child from identifiers extracted from the record headers, or a new span if
   * one couldn't be extracted.
   */
  public Span nextSpan(ConsumerRecord<?, ?> record) {
    TraceContextOrSamplingFlags extracted = extractAndClearHeaders(record);
    Span result = tracing.tracer().nextSpan(extracted);
    if (extracted.context() == null && !result.isNoop()) {
      addTags(record, result);
    }
    return result;
  }

  TraceContextOrSamplingFlags extractAndClearHeaders(ConsumerRecord<?, ?> record) {
    TraceContextOrSamplingFlags extracted = extractor.extract(record.headers());
    // clear propagation headers if we were able to extract a span
    if (extracted != TraceContextOrSamplingFlags.EMPTY) {
      tracing.propagation().keys().forEach(key -> record.headers().remove(key));
    }
    return extracted;
  }

  /** When an upstream context was not present, lookup keys are unlikely added */
  static void addTags(ConsumerRecord<?, ?> record, SpanCustomizer result) {
    if (record.key() instanceof String && !"".equals(record.key())) {
      result.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
    }
    result.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());
  }
}

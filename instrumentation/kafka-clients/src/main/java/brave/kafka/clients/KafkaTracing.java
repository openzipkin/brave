package brave.kafka.clients;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;
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

  final Tracing tracing;
  final TraceContext.Extractor<Headers> extractor;
  final TraceContext.Injector<Headers> injector;
  final List<String> propagationKeys;
  final String remoteServiceName;

  KafkaTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaPropagation.GETTER);
    this.injector = tracing.propagation().injector(KafkaPropagation.SETTER);
    this.propagationKeys = builder.tracing.propagation().keys();
    this.remoteServiceName = builder.remoteServiceName;
  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link
   * #nextSpan(ConsumerRecord)}.
   */
  public <K, V> Consumer<K, V> consumer(Consumer<K, V> consumer) {
    return new TracingConsumer<>(consumer, this);
  }

  /** Starts and propagates {@link Span.Kind#PRODUCER} span for each message sent. */
  public <K, V> Producer<K, V> producer(Producer<K, V> producer) {
    return new TracingProducer<>(producer, this);
  }

  /**
   * Use this to create a span for processing the given record. Note: the result has no name and is
   * not started.
   *
   * <p>This creates a child from identifiers extracted from the record headers, or a new span if
   * one couldn't be extracted.
   */
  public Span nextSpan(ConsumerRecord<?, ?> record) {
    TraceContextOrSamplingFlags extracted = extractAndClearHeaders(record.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    if (extracted.context() == null && !result.isNoop()) {
      addTags(record, result);
    }
    return result;
  }

  TraceContextOrSamplingFlags extractAndClearHeaders(Headers headers) {
    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    // clear propagation headers if we were able to extract a span
    if (!extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
      clearHeaders(headers);
    }
    return extracted;
  }

  void clearHeaders(Headers headers) {
    for (int i = 0, length = propagationKeys.size(); i < length; i++) {
      headers.remove(propagationKeys.get(i));
    }
  }

  /** When an upstream context was not present, lookup keys are unlikely added */
  static void addTags(ConsumerRecord<?, ?> record, SpanCustomizer result) {
    if (record.key() instanceof String && !"".equals(record.key())) {
      result.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
    }
    result.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());
  }
}

package brave.kafka.streams;

import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;

class TracingValueTransformerSupplier<V, VR> implements ValueTransformerSupplier<V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ValueTransformer<V, VR> delegateTransformer;

  TracingValueTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String spanName,
      ValueTransformer<V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformer<V, VR> get() {
    return new TracingValueTransformer<>(kafkaStreamsTracing, spanName, delegateTransformer);
 }
}

package brave.kafka.streams;

import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;

class TracingValueTransformerWithKeySupplier<K, V, VR> implements
    ValueTransformerWithKeySupplier<K, V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ValueTransformerWithKey<K, V, VR> delegateTransformer;

  TracingValueTransformerWithKeySupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String spanName,
      ValueTransformerWithKey<K, V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformerWithKey<K, V, VR> get() {
    return new TracingValueTransformerWithKey<>(kafkaStreamsTracing, spanName, delegateTransformer);
 }
}

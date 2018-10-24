package brave.kafka.streams;

import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;

class TracingValueTransformerSupplier<V, VR> implements ValueTransformerSupplier<V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String name;
  final ValueTransformer<V, VR> delegateTransformer;

  TracingValueTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String name,
      ValueTransformer<V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.name = name;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformer<V, VR> get() {
    return new TracingValueTransformer<>(kafkaStreamsTracing, name, delegateTransformer);
 }
}

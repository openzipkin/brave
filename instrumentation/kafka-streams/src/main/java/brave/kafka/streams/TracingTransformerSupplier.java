package brave.kafka.streams;

import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;

class TracingTransformerSupplier<K, V, R> implements TransformerSupplier<K, V, R> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String name;
  final Transformer<K, V, R> delegateTransformer;

  TracingTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String name,
      Transformer<K, V, R> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.name = name;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public Transformer<K, V, R> get() {
    return new TracingTransformer<>(kafkaStreamsTracing, name, delegateTransformer);
 }
}

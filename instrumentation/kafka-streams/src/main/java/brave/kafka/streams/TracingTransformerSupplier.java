package brave.kafka.streams;

import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;

class TracingTransformerSupplier<K, V, R> implements TransformerSupplier<K, V, R> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Transformer<K, V, R> delegateTransformer;

  TracingTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String spanName,
      Transformer<K, V, R> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public Transformer<K, V, R> get() {
    return new TracingTransformer<>(kafkaStreamsTracing, spanName, delegateTransformer);
 }
}

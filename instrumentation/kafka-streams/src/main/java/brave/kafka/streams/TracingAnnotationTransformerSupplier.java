package brave.kafka.streams;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;

class TracingAnnotationTransformerSupplier<K, V> implements TransformerSupplier<K, V, KeyValue<K, V>> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String annotation;

  TracingAnnotationTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String annotation) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.annotation = annotation;
  }

  /** This wraps transform method to enable tracing. */
  @Override public Transformer<K, V, KeyValue<K, V>> get() {
    return new TracingAnnotationTransformer<>(kafkaStreamsTracing, annotation);
 }
}

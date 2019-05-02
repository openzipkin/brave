package brave.kafka.streams;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;

public class TracingFilterTransformerSupplier<K, V> implements TransformerSupplier<K, V, KeyValue<K, V>> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Predicate<K, V> delegatePredicate;
  final boolean filterNot;

  public TracingFilterTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String spanName, Predicate<K, V> delegatePredicate, boolean filterNot) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegatePredicate = delegatePredicate;
    this.filterNot = filterNot;
  }

  @Override public Transformer<K, V, KeyValue<K, V>> get() {
    return new TracingFilterTransformer<>(kafkaStreamsTracing, spanName, delegatePredicate,
        filterNot);
  }
}

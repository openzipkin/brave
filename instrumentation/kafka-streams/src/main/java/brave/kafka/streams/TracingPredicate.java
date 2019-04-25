package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Predicate;

class TracingPredicate<K, V> extends AbstractTracingTransformer<K, V, KeyValue<K, V>> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Predicate<K, V> delegatePredicate;

  TracingPredicate(KafkaStreamsTracing tracing, String spanName,
      Predicate<K, V> delegatePredicate) {
    this.kafkaStreamsTracing = tracing;
    this.spanName = spanName;
    this.delegatePredicate = delegatePredicate;
  }

  @Override
  public KeyValue<K, V> transform(K key, V value) {
    Span span = kafkaStreamsTracing.tracing.tracer().nextSpan();
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    try (Tracer.SpanInScope ws = kafkaStreamsTracing.tracing.tracer().withSpanInScope(span)) {
      if (delegatePredicate.test(key, value)) {
        return KeyValue.pair(key, value);
      } else {
        span.annotate(KafkaStreamsTags.KAFKA_STREAMS_FILTERED_TAG);
        return null; // meaning KV pair will not be forwarded thus effectively filtered
      }
    } catch (RuntimeException | Error e) {
      span.error(e); // finish as an exception means the callback won't finish the span
      throw e;
    } finally {
      span.finish();
    }
  }
}

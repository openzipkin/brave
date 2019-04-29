package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.processor.ProcessorContext;

import static brave.kafka.streams.KafkaStreamsTags.KAFKA_STREAMS_FILTERED_TAG;

class TracingFilterTransformer<K, V> extends AbstractTracingTransformer<K, V, KeyValue<K, V>> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Predicate<K, V> delegatePredicate;
  final Tracer tracer;
  final boolean filterNot;
  ProcessorContext processorContext;

  TracingFilterTransformer(KafkaStreamsTracing tracing, String spanName,
      Predicate<K, V> delegatePredicate, boolean filterNot) {
    this.kafkaStreamsTracing = tracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.spanName = spanName;
    this.delegatePredicate = delegatePredicate;
    this.filterNot = filterNot;
  }

  @Override public void init(ProcessorContext context) {
    processorContext = context;
  }

  @Override
  public KeyValue<K, V> transform(K key, V value) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      if (filterNot ^ delegatePredicate.test(key, value)) {
        span.tag(KAFKA_STREAMS_FILTERED_TAG, "false");
        return KeyValue.pair(key, value);
      } else {
        span.tag(KAFKA_STREAMS_FILTERED_TAG, "true");
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

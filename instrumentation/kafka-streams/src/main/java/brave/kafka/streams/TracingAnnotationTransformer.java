package brave.kafka.streams;

import brave.Tracer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class TracingAnnotationTransformer<K, V> implements Transformer<K, V, KeyValue<K, V>> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String annotation;

  ProcessorContext processorContext;

  TracingAnnotationTransformer(KafkaStreamsTracing kafkaStreamsTracing, String annotation) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.annotation = annotation;
  }

  @Override public void init(ProcessorContext context) {
    this.processorContext = context;
  }

  @Override public KeyValue<K, V> transform(K key, V value) {
    kafkaStreamsTracing.annotateSpan(processorContext, annotation);
    return KeyValue.pair(key, value);
  }

  @Override public void close() {
  }
}

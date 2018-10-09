package brave.kafka.streams;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;

class TracingProcessorSupplier<K, V> implements ProcessorSupplier<K, V> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String name;
  final Processor<K, V> delegateProcessor;

  TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String name,
      Processor<K, V> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.name = name;
    this.delegateProcessor = delegateProcessor;
  }

  /** This wraps process method to enable tracing. */
  @Override public Processor<K, V> get() {
    return new TracingProcessor<>(kafkaStreamsTracing, name, delegateProcessor);
  }
}

package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

class TracingProcessorSupplier<K, V> implements ProcessorSupplier<K, V> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String name;
  final Processor<K, V> delegateProcessor;

  TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
      String name,
      Processor<K, V> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.name = name;
    this.delegateProcessor = delegateProcessor;
  }

  /** This wraps transform method to add tracing. */
  @Override public Processor<K, V> get() {
    return new Processor<K, V>() {
      ProcessorContext processorContext;

      @Override
      public void init(ProcessorContext processorContext) {
        this.processorContext = processorContext;
        delegateProcessor.init(processorContext);
      }

      @Override
      public void process(K k, V v) {
        Span span = kafkaStreamsTracing.nextSpan(processorContext);
        if (!span.isNoop()) {
          span.name(name);
          if (k instanceof String && !"".equals(k)) {
            span.tag(KafkaStreamsTags.KAFKA_STREAMS_KEY_TAG, k.toString());
          }
          span.start();
        }

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          delegateProcessor.process(k, v);
        } catch (RuntimeException | Error e) {
          span.error(e); // finish as an exception means the callback won't finish the span
          throw e;
        } finally {
          span.finish();
        }
      }

      @Override
      public void close() {
        delegateProcessor.close();
      }
    };
  }
}

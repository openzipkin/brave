package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

class TracingTransformer<K, V, R> implements Transformer<K, V, R> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final Transformer<K, V, R> delegateTransformer;

  ProcessorContext processorContext;

  TracingTransformer(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
      Transformer<K, V, R> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
  }

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
    delegateTransformer.init(processorContext);
  }

  @Override
  public R transform(K k, V v) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return delegateTransformer.transform(k, v);
    } catch (RuntimeException | Error e) {
      span.error(e); // finish as an exception means the callback won't finish the span
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void close() {
    delegateTransformer.close();
  }
}



package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;

class TracingValueTransformer<V, VR> implements ValueTransformer<V, VR> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final ValueTransformer<V, VR> delegateTransformer;

  ProcessorContext processorContext;

  TracingValueTransformer(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
      ValueTransformer<V, VR> delegateTransformer) {
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
  public VR transform(V v) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return delegateTransformer.transform(v);
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



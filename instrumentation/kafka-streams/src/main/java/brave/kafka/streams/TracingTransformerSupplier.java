package brave.kafka.streams;

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 *
 */
class TracingTransformerSupplier<K, V, R> implements TransformerSupplier<K, V, R> {
    final KafkaStreamsTracing kafkaStreamsTracing;
    final Tracer tracer;
    final String name;
    final Transformer<K, V, R> delegateTransformer;

    TracingTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
                                      String name,
                                      Transformer<K, V, R> delegateTransformer) {
        this.kafkaStreamsTracing = kafkaStreamsTracing;
        this.tracer = kafkaStreamsTracing.tracing.tracer();
        this.name = name;
        this.delegateTransformer = delegateTransformer;
    }

    @Override
    public Transformer<K, V, R> get() {
        return new Transformer<K, V, R>() {
            ProcessorContext processorContext;

            @Override
            public void init(ProcessorContext processorContext) {
                this.processorContext = processorContext;
                delegateTransformer.init(processorContext);
            }

            @Override
            public R transform(K k, V v) {
                Span span = kafkaStreamsTracing.nextSpan(processorContext);
                if (!span.isNoop()) {
                    span.name(name);
                    if (k instanceof String && !"".equals(k)) {
                        span.tag(KafkaStreamsTags.KAFKA_STREAMS_KEY_TAG, k.toString());
                    }
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
        };
    }
}

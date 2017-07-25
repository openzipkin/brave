package brave.kafka;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import zipkin.internal.Util;

/**
 * Util class which creates a child span according to the headers.
 */
public class RecordTracing {

    private final TraceContext.Extractor<Headers> extractor;
    private final Tracing tracing;

    RecordTracing(Tracing tracing) {
        this.tracing = tracing;
        this.extractor = tracing.propagation().extractor(
                (carrier, key) -> {
                    Header header = carrier.lastHeader(key);
                    if (header == null) return null;
                    return new String(header.value(), Util.UTF_8);
                });
    }

    /**
     * Continues the trace extracted from the headers or creates a new one if the extract fails.
     * Call this method while consumming your kafka records.
     */
    public Span nexSpanFromRecord(ConsumerRecord record) {
        Span newChild;
        TraceContext context = extractor.extract(record.headers()).context();
        if (context != null) {
            newChild = tracing.tracer().newChild(context);
        } else {
            // Should we throw an exception ? Create a new span ?
            newChild = tracing.tracer().nextSpan();
        }
        return newChild;
    }

    /**
     * Close the producer async span extracted from the headers.
     */
    void finishProducerSpan(ConsumerRecord record) {
        TraceContext context = extractor.extract(record.headers()).context();
        if (context != null) {
            Span span = tracing.tracer().joinSpan(context);
            span.kind(Span.Kind.SERVER).start().flush();
        }
    }
}

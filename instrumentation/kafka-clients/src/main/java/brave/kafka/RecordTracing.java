package brave.kafka;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import zipkin.internal.Util;

/**
 * Util class which creates a child span according to the headers.
 */
public final class RecordTracing {

  private final TraceContext.Extractor<Headers> extractor;
  private final Tracer tracer;

  RecordTracing(Tracing tracing) {
    this.tracer = tracing.tracer();
    this.extractor = tracing.propagation().extractor(
        (carrier, key) -> {
          Header header = carrier.lastHeader(key);
          if (header == null) return null;
          return new String(header.value(), Util.UTF_8);
        });
  }

  /**
   * Continues the trace extracted from the headers or creates a new one if the extract fails. Call
   * this method while consumming your kafka records.
   */
  public Span nextSpan(ConsumerRecord record) {
    TraceContext context = extractor.extract(record.headers()).context();
    if (context != null) {
      return tracer.newChild(context);
    } else {
      return tracer.nextSpan();
    }
  }

  /**
   * Close the producer async span extracted from the headers.
   */
  void maybeFinishProducerSpan(ConsumerRecord record) {
    TraceContext context = extractor.extract(record.headers()).context();
    if (context != null) {
      Span span = tracer.joinSpan(context);
      span.kind(Span.Kind.SERVER).start().flush();
    }
  }
}

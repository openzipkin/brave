package brave.internal.recorder;

import brave.Span;
import brave.propagation.TraceContext;
import javax.annotation.Nullable;
import zipkin.Endpoint;
import zipkin.internal.Span2;

final class MutableSpan {
  final Span2.Builder span;
  boolean finished;
  long timestamp;

  // Since this is not exposed, this class could be refactored later as needed to act in a pool
  // to reduce GC churn. This would involve calling span.clear and resetting the fields below.
  MutableSpan(TraceContext context, Endpoint localEndpoint) {
    this.span = Span2.builder()
        .traceIdHigh(context.traceIdHigh())
        .traceId(context.traceId())
        .parentId(context.parentId())
        .id(context.spanId())
        .debug(context.debug())
        .shared(context.shared())
        .localEndpoint(localEndpoint);
    finished = false;
  }

  synchronized MutableSpan start(long timestamp) {
    span.timestamp(this.timestamp = timestamp);
    return this;
  }

  synchronized MutableSpan name(String name) {
    span.name(name);
    return this;
  }

  synchronized MutableSpan kind(Span.Kind kind) {
    try {
      span.kind(Span2.Kind.valueOf(kind.name()));
    } catch (IllegalArgumentException e) {
      // TODO: log
    }
    return this;
  }

  synchronized MutableSpan annotate(long timestamp, String value) {
    span.addAnnotation(timestamp, value);
    return this;
  }

  synchronized MutableSpan tag(String key, String value) {
    span.putTag(key, value);
    return this;
  }

  synchronized MutableSpan remoteEndpoint(Endpoint remoteEndpoint) {
    span.remoteEndpoint(remoteEndpoint);
    return this;
  }

  /** Completes and reports the span */
  synchronized MutableSpan finish(@Nullable Long finishTimestamp) {
    if (finished) return this;
    finished = true;

    if (timestamp != 0 && finishTimestamp != null) {
      span.duration(Math.max(finishTimestamp - timestamp, 1));
    }
    return this;
  }

  synchronized Span2 toSpan() {
    return span.build();
  }
}

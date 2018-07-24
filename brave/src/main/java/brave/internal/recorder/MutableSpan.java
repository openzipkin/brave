package brave.internal.recorder;

import brave.Clock;
import brave.Span;
import brave.internal.HexCodec;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;

final class MutableSpan {
  final Clock clock;
  final zipkin2.Span.Builder span;
  long timestamp;

  // Since this is not exposed, this class could be refactored later as needed to act in a pool
  // to reduce GC churn. This would involve calling span.clear and resetting the fields below.
  MutableSpan(Clock clock, TraceContext context) {
    this.clock = clock;
    long parentId = context.parentIdAsLong();
    this.span = zipkin2.Span.newBuilder()
        .traceId(context.traceIdString())
        .parentId(parentId != 0L ? HexCodec.toLowerHex(parentId) : null)
        .id(HexCodec.toLowerHex(context.spanId()))
        .debug(context.debug() ? true : null)
        .shared(context.shared() ? true : null);
  }

  void start() {
    start(clock.currentTimeMicroseconds());
  }

  synchronized void start(long timestamp) {
    span.timestamp(this.timestamp = timestamp);
  }

  synchronized void name(String name) {
    span.name(name);
  }

  synchronized void kind(Span.Kind kind) {
    try {
      span.kind(zipkin2.Span.Kind.valueOf(kind.name()));
    } catch (IllegalArgumentException e) {
      // TODO: log
    }
  }

  void annotate(String value) {
    annotate(clock.currentTimeMicroseconds(), value);
  }

  synchronized void annotate(long timestamp, String value) {
    if ("cs".equals(value)) {
      span.kind(zipkin2.Span.Kind.CLIENT).timestamp(this.timestamp = timestamp);
    } else if ("sr".equals(value)) {
      span.kind(zipkin2.Span.Kind.SERVER).timestamp(this.timestamp = timestamp);
    } else if ("cr".equals(value)) {
      span.kind(zipkin2.Span.Kind.CLIENT);
      finish(timestamp);
    } else if ("ss".equals(value)) {
      span.kind(zipkin2.Span.Kind.SERVER);
      finish(timestamp);
    } else {
      span.addAnnotation(timestamp, value);
    }
  }

  synchronized void tag(String key, String value) {
    span.putTag(key, value);
  }

  synchronized void remoteEndpoint(Endpoint remoteEndpoint) {
    span.remoteEndpoint(remoteEndpoint);
  }

  /** Completes and reports the span */
  synchronized void finish(long finishTimestamp) {
    if (timestamp != 0L && finishTimestamp != 0L) {
      span.duration(Math.max(finishTimestamp - timestamp, 1));
    }
  }

  synchronized zipkin2.Span toSpan(Endpoint localEndpoint) {
    return span.localEndpoint(localEndpoint).build();
  }
}

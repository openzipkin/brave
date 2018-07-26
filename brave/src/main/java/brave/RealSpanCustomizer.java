package brave;

import brave.internal.recorder.SpanRecord;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpanCustomizer implements SpanCustomizer {

  final TraceContext context;
  final SpanRecord record;
  final Clock clock;

  RealSpanCustomizer(TraceContext context, SpanRecord record, Clock clock) {
    this.context = context;
    this.record = record;
    this.clock = clock;
  }

  @Override public SpanCustomizer name(String name) {
    synchronized (record) {
      record.name(name);
    }
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    long timestamp = clock.currentTimeMicroseconds();
    synchronized (record) {
      record.annotate(timestamp, value);
    }
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    synchronized (record) {
      record.tag(key, value);
    }
    return this;
  }

  @Override
  public String toString() {
    return "RealSpanCustomizer(" + context + ")";
  }
}

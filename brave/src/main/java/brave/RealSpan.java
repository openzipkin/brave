package brave;

import brave.Tracing.SpanReporter;
import brave.internal.recorder.PendingSpanRecords;
import brave.internal.recorder.SpanRecord;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final PendingSpanRecords pendingSpanRecords;
  final SpanRecord record;
  final Clock clock;
  final SpanReporter spanReporter;
  final ErrorParser errorParser;
  final RealSpanCustomizer customizer;

  RealSpan(TraceContext context,
      PendingSpanRecords pendingSpanRecords,
      SpanRecord record,
      Clock clock,
      SpanReporter spanReporter,
      ErrorParser errorParser) {
    this.context = context;
    this.pendingSpanRecords = pendingSpanRecords;
    this.record = record;
    this.clock = clock;
    this.customizer = new RealSpanCustomizer(context, record, clock);
    this.spanReporter = spanReporter;
    this.errorParser = errorParser;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public SpanCustomizer customizer() {
    return new RealSpanCustomizer(context, record, clock);
  }

  @Override public Span start() {
    return start(clock.currentTimeMicroseconds());
  }

  @Override public Span start(long timestamp) {
    synchronized (record) {
      record.startTimestamp(timestamp);
    }
    return this;
  }

  @Override public Span name(String name) {
    synchronized (record) {
      record.name(name);
    }
    return this;
  }

  @Override public Span kind(Kind kind) {
    synchronized (record) {
      record.kind(kind);
    }
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(clock.currentTimeMicroseconds(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    // Modern instrumentation should not send annotations such as this, but we leniently
    // accept them rather than fail. This for example allows old bridges like to Brave v3 to work
    if ("cs".equals(value)) {
      synchronized (record) {
        record.kind(Span.Kind.CLIENT);
        record.startTimestamp(timestamp);
      }
    } else if ("sr".equals(value)) {
      synchronized (record) {
        record.kind(Span.Kind.SERVER);
        record.startTimestamp(timestamp);
      }
    } else if ("cr".equals(value)) {
      synchronized (record) {
        record.kind(Span.Kind.CLIENT);
      }
      finish(timestamp);
    } else if ("ss".equals(value)) {
      synchronized (record) {
        record.kind(Span.Kind.SERVER);
      }
      finish(timestamp);
    } else {
      synchronized (record) {
        record.annotate(timestamp, value);
      }
    }
    return this;
  }

  @Override public Span tag(String key, String value) {
    synchronized (record) {
      record.tag(key, value);
    }
    return this;
  }

  @Override public Span error(Throwable throwable) {
    errorParser.error(throwable, customizer());
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    synchronized (record) {
      record.remoteEndpoint(remoteEndpoint);
    }
    return this;
  }

  @Override public void finish() {
    finish(clock.currentTimeMicroseconds());
  }

  @Override public void finish(long timestamp) {
    if (!pendingSpanRecords.remove(context)) return;
    synchronized (record) {
      record.finishTimestamp(timestamp);
    }
    spanReporter.report(context, record);
  }

  @Override public void abandon() {
    pendingSpanRecords.remove(context);
  }

  @Override public void flush() {
    abandon();
    spanReporter.report(context, record);
  }

  @Override public String toString() {
    return "RealSpan(" + context + ")";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RealSpan)) return false;
    return context.equals(((RealSpan) o).context);
  }

  @Override public int hashCode() {
    return context.hashCode();
  }
}

package brave;

import brave.Tracing.SpanReporter;
import brave.internal.recorder.PendingSpanRecords;
import brave.internal.recorder.SpanRecord;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealScopedSpan extends ScopedSpan {

  final TraceContext context;
  final Scope scope;
  final SpanRecord record;
  final Clock clock;
  final PendingSpanRecords pendingSpanRecords;
  final SpanReporter spanReporter;
  final ErrorParser errorParser;

  RealScopedSpan(
      TraceContext context,
      Scope scope,
      SpanRecord record,
      Clock clock,
      PendingSpanRecords pendingSpanRecords,
      SpanReporter spanReporter,
      ErrorParser errorParser
  ) {
    this.context = context;
    this.scope = scope;
    this.pendingSpanRecords = pendingSpanRecords;
    this.record = record;
    this.clock = clock;
    this.spanReporter = spanReporter;
    this.errorParser = errorParser;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public ScopedSpan annotate(String value) {
    record.annotate(clock.currentTimeMicroseconds(), value);
    return this;
  }

  @Override public ScopedSpan tag(String key, String value) {
    record.tag(key, value);
    return this;
  }

  @Override public ScopedSpan error(Throwable throwable) {
    errorParser.error(throwable, this);
    return this;
  }

  @Override public void finish() {
    scope.close();
    if (!pendingSpanRecords.remove(context)) return; // don't double-report
    spanReporter.report(context, record);
    record.finishTimestamp(clock.currentTimeMicroseconds());
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RealScopedSpan)) return false;
    RealScopedSpan that = (RealScopedSpan) o;
    return context.equals(that.context) && scope.equals(that.scope);
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= context.hashCode();
    h *= 1000003;
    h ^= scope.hashCode();
    return h;
  }
}

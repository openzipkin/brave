package brave.internal.recorder;

import brave.Clock;

public final class PendingSpanRecord {
  final SpanRecord span;
  final TickClock clock;

  PendingSpanRecord(SpanRecord span, TickClock clock) {
    this.span = span;
    this.clock = clock;
  }

  public SpanRecord span() {
    return span;
  }

  /** Returns a clock that ensures startTimestamp consistency across the trace */
  public Clock clock() {
    return clock;
  }
}

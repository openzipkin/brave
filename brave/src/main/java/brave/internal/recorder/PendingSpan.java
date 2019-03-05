package brave.internal.recorder;

import brave.Clock;
import brave.handler.MutableSpan;

public final class PendingSpan {
  final MutableSpan state;
  final TickClock clock;
  volatile Throwable caller;

  PendingSpan(MutableSpan state, TickClock clock) {
    this.state = state;
    this.clock = clock;
  }

  /** Returns the state currently accumulated for this trace ID and span ID */
  public MutableSpan state() {
    return state;
  }

  /** Returns a clock that ensures startTimestamp consistency across the trace */
  public Clock clock() {
    return clock;
  }
}

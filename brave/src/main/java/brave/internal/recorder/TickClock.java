package brave.internal.recorder;

import brave.Clock;

final class TickClock implements Clock {
  final long baseEpochMicros;
  final long baseTickNanos;

  TickClock(long baseEpochMicros, long baseTickNanos) {
    this.baseEpochMicros = baseEpochMicros;
    this.baseTickNanos = baseTickNanos;
  }

  @Override public long currentTimeMicroseconds() {
    return ((System.nanoTime() - baseTickNanos) / 1000) + baseEpochMicros;
  }

  @Override public String toString() {
    return "TickClock{"
        + "baseEpochMicros=" + baseEpochMicros + ", "
        + "baseTickNanos=" + baseTickNanos
        + "}";
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.recorder;

import brave.Clock;
import brave.internal.Platform;

final class TickClock implements Clock {
  final Platform platform;
  final long baseEpochMicros;
  final long baseTickNanos;

  TickClock(Platform platform, long baseEpochMicros, long baseTickNanos) {
    this.platform = platform;
    this.baseEpochMicros = baseEpochMicros;
    this.baseTickNanos = baseTickNanos;
  }

  @Override public long currentTimeMicroseconds() {
    return ((platform.nanoTime() - baseTickNanos) / 1000) + baseEpochMicros;
  }

  @Override public String toString() {
    return "TickClock{"
      + "baseEpochMicros=" + baseEpochMicros + ", "
      + "baseTickNanos=" + baseTickNanos
      + "}";
  }
}

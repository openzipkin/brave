/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

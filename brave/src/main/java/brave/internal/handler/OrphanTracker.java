/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.internal.handler;

import brave.Clock;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.collect.WeakConcurrentMap;
import brave.propagation.TraceContext;

/** Internal support class for {@link Tracing.Builder#trackOrphans()}. */
public final class OrphanTracker extends SpanHandler {
  final Clock clock; // only used when a span is orphaned
  final WeakConcurrentMap<MutableSpan, Throwable> spanToCaller = new WeakConcurrentMap<>();

  public OrphanTracker(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean begin(TraceContext context, MutableSpan span, @Nullable TraceContext parent) {
    Throwable oldCaller = spanToCaller.putIfProbablyAbsent(span,
      new Throwable("Thread " + Thread.currentThread().getName() + " allocated span here"));
    assert oldCaller == null :
      "Bug: unexpected to have an existing reference to a new MutableSpan!";
    return true;
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    Throwable caller = spanToCaller.remove(span);
    if (cause != Cause.ORPHANED && caller != null) {
      String message = span.equals(new MutableSpan(context, null))
        ? "Span " + context + " was allocated but never used"
        : "Span " + context + " neither finished nor flushed before GC";
      Platform.get().log(message, caller);
    }
    span.annotate(clock.currentTimeMicroseconds(), "brave.flush");
    return true;
  }
}

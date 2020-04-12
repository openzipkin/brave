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
package brave.internal.recorder;

import brave.Clock;
import brave.Tracer;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.weaklockfree.WeakConcurrentMap;
import brave.propagation.TraceContext;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Similar to Finagle's deadline span map, except this is GC pressure as opposed to timeout driven.
 * This means there's no bookkeeping thread required in order to flush orphaned spans. Work here is
 * stolen from callers, though. For example, a call to {@link Tracer#nextSpan()} implicitly performs
 * a check for orphans, invoking any handler that applies.
 *
 * <p>Spans are weakly referenced by their owning context. When the keys are collected, they are
 * transferred to a queue, waiting to be reported. A call to modify any span will implicitly flush
 * orphans to Zipkin. Spans in this state will have a "brave.flush" annotation added to them.
 */
public final class PendingSpans extends WeakConcurrentMap<TraceContext, PendingSpan> {
  @Nullable final WeakConcurrentMap<MutableSpan, Throwable> spanToCaller;
  final Clock clock;
  final FinishedSpanHandler orphanedSpanHandler;
  final AtomicBoolean noop;

  public PendingSpans(Clock clock, FinishedSpanHandler orphanedSpanHandler, boolean trackOrphans,
    AtomicBoolean noop) {
    this.clock = clock;
    this.orphanedSpanHandler = orphanedSpanHandler;
    this.spanToCaller = trackOrphans ? new WeakConcurrentMap<>() : null;
    this.noop = noop;
  }

  /**
   * Gets a pending span, or returns {@code null} if there is none.
   *
   * <p>This saves processing time and ensures reference consistency by looking for an existing,
   * decorated context first. This ensures an externalized, but existing context is not mistaken for
   * a new local root.
   */
  @Nullable public PendingSpan get(TraceContext context) {
    return getIfPresent(context);
  }

  public PendingSpan getOrCreate(
    @Nullable TraceContext parent, TraceContext context, boolean start) {
    PendingSpan result = get(context);
    if (result != null) return result;

    MutableSpan data = new MutableSpan();
    if (context.shared()) data.setShared();

    PendingSpan parentSpan = parent != null ? get(parent) : null;

    // save overhead calculating time if the parent is in-progress (usually is)
    TickClock clock;
    if (parentSpan != null) {
      clock = parentSpan.clock;
      if (start) data.startTimestamp(clock.currentTimeMicroseconds());
    } else {
      long currentTimeMicroseconds = this.clock.currentTimeMicroseconds();
      clock = new TickClock(currentTimeMicroseconds, System.nanoTime());
      if (start) data.startTimestamp(currentTimeMicroseconds);
    }

    PendingSpan newSpan = new PendingSpan(context, data, clock);
    // Probably absent because we already checked with get() at the entrance of this method
    PendingSpan previousSpan = putIfProbablyAbsent(context, newSpan);
    if (previousSpan != null) return previousSpan; // lost race

    // We've now allocated a new trace context.
    assert parent != null || context.isLocalRoot() :
      "Bug (or unexpected call to internal code): parent can only be null in a local root!";

    if (spanToCaller != null) {
      Throwable oldCaller = spanToCaller.putIfProbablyAbsent(newSpan.state,
        new Throwable("Thread " + Thread.currentThread().getName() + " allocated span here"));
      assert oldCaller == null :
        "Bug: unexpected to have an existing reference to a new MutableSpan!";
    }
    return newSpan;
  }

  /** @see brave.Span#abandon() */
  public void abandon(TraceContext context) {
    remove(context);
  }

  /** @see brave.Span#finish() */
  @Override public PendingSpan remove(TraceContext context) {
    return super.remove(context);
  }

  /** Reports spans orphaned by garbage collection. */
  @Override protected void expungeStaleEntries() {
    Reference<?> reference;
    // This is called on critical path of unrelated traced operations. If we have orphaned spans, be
    // careful to not penalize the performance of the caller. It is better to cache time when
    // flushing a span than hurt performance of unrelated operations by calling
    // currentTimeMicroseconds N times
    long flushTime = 0L;
    boolean noop = orphanedSpanHandler == FinishedSpanHandler.NOOP || this.noop.get();
    while ((reference = poll()) != null) {
      PendingSpan value = removeStaleEntry(reference);
      if (noop || value == null) continue;
      assert value.context() == null : "unexpected for the weak referent to be present after GC!";
      if (flushTime == 0L) flushTime = clock.currentTimeMicroseconds();

      boolean isEmpty = value.state.isEmpty();
      Throwable caller = spanToCaller != null ? spanToCaller.getIfPresent(value.state) : null;

      TraceContext context = value.backupContext;

      if (caller != null) {
        String message = isEmpty
          ? "Span " + context + " was allocated but never used"
          : "Span " + context + " neither finished nor flushed before GC";
        Platform.get().log(message, caller);
      }
      if (isEmpty) continue;

      value.state.annotate(flushTime, "brave.flush");
      orphanedSpanHandler.handle(context, value.state);
    }
  }
}

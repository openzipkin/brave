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
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.handler.SpanHandler.Cause;
import brave.internal.Nullable;
import brave.internal.collect.WeakConcurrentMap;
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
  final MutableSpan defaultSpan;
  final Clock clock;
  final SpanHandler spanHandler;
  final AtomicBoolean noop;

  public PendingSpans(MutableSpan defaultSpan, Clock clock, SpanHandler spanHandler,
    AtomicBoolean noop) {
    this.defaultSpan = defaultSpan;
    this.clock = clock;
    this.spanHandler = spanHandler;
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

    MutableSpan span = new MutableSpan(context, defaultSpan);
    PendingSpan parentSpan = parent != null ? get(parent) : null;

    // save overhead calculating time if the parent is in-progress (usually is)
    TickClock clock;
    if (parentSpan != null) {
      TraceContext parentContext = parentSpan.context();
      if (parentContext != null) parent = parentContext;
      clock = parentSpan.clock;
      if (start) span.startTimestamp(clock.currentTimeMicroseconds());
    } else {
      long currentTimeMicroseconds = this.clock.currentTimeMicroseconds();
      clock = new TickClock(currentTimeMicroseconds, System.nanoTime());
      if (start) span.startTimestamp(currentTimeMicroseconds);
    }

    PendingSpan newSpan = new PendingSpan(context, span, clock);
    // Probably absent because we already checked with get() at the entrance of this method
    PendingSpan previousSpan = putIfProbablyAbsent(context, newSpan);
    if (previousSpan != null) return previousSpan; // lost race

    // We've now allocated a new trace context.
    assert parent != null || context.isLocalRoot() :
      "Bug (or unexpected call to internal code): parent can only be null in a local root!";

    spanHandler.begin(newSpan.handlerContext, newSpan.span, parentSpan != null
      ? parentSpan.handlerContext : null);
    return newSpan;
  }

  /** @see brave.Span#abandon() */
  public void abandon(TraceContext context) {
    PendingSpan last = remove(context);
    if (last != null) spanHandler.end(last.handlerContext, last.span, Cause.ABANDONED);
  }

  /** @see brave.Span#flush() */
  public void flush(TraceContext context) {
    PendingSpan last = remove(context);
    if (last != null) spanHandler.end(last.handlerContext, last.span, Cause.FLUSHED);
  }

  /**
   * Completes the span associated with this context, if it hasn't already been finished.
   *
   * @param timestamp zero means use the current time
   * @see brave.Span#finish()
   */
  // zero here allows us to skip overhead of using the clock when the span already finished!
  public void finish(TraceContext context, long timestamp) {
    PendingSpan last = remove(context);
    if (last == null) return;
    last.span.finishTimestamp(timestamp != 0L ? timestamp : last.clock.currentTimeMicroseconds());
    spanHandler.end(last.handlerContext, last.span, Cause.FINISHED);
  }

  /** Reports spans orphaned by garbage collection. */
  @Override protected void expungeStaleEntries() {
    Reference<?> reference;
    boolean noop = this.noop.get();
    while ((reference = poll()) != null) {
      PendingSpan value = removeStaleEntry(reference);
      if (noop || value == null) continue;
      assert value.context() == null : "unexpected for the weak referent to be present after GC!";
      spanHandler.end(value.handlerContext, value.span, Cause.ORPHANED);
    }
  }
}

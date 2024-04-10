/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.recorder;

import brave.Clock;
import brave.handler.MutableSpan;
import brave.internal.InternalPropagation;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.ref.WeakReference;

/**
 * This is the value of a map entry in {@link PendingSpans}, whose key is a weak reference to {@link
 * #context()}.
 *
 * <p>{@link #context()} is cached so that externalized forms of a trace context to be swapped for
 * the one in use. It is a weak reference as otherwise it would prevent the corresponding map key
 * from being garbage collected.
 */
public final class PendingSpan extends WeakReference<TraceContext> {
  final MutableSpan span;
  final TickClock clock;
  final TraceContext handlerContext;

  PendingSpan(TraceContext context, MutableSpan span, TickClock clock) {
    super(context);
    this.span = span;
    this.clock = clock;
    this.handlerContext = InternalPropagation.instance.shallowCopy(context);
  }

  /** Returns the context for this span unless it was cleared due to GC. */
  @Nullable public TraceContext context() {
    return get();
  }

  /** Returns the state currently accumulated for this trace ID and span ID */
  public MutableSpan state() {
    return span;
  }

  /** Returns a clock that ensures startTimestamp consistency across the trace */
  public Clock clock() {
    return clock;
  }
}

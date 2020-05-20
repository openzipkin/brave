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

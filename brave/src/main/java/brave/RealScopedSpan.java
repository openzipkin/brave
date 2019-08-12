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
package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.recorder.PendingSpans;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealScopedSpan extends ScopedSpan {

  final TraceContext context;
  final Scope scope;
  final MutableSpan state;
  final Clock clock;
  final PendingSpans pendingSpans;
  final FinishedSpanHandler finishedSpanHandler;

  RealScopedSpan(
    TraceContext context,
    Scope scope,
    MutableSpan state,
    Clock clock,
    PendingSpans pendingSpans,
    FinishedSpanHandler finishedSpanHandler
  ) {
    this.context = context;
    this.scope = scope;
    this.pendingSpans = pendingSpans;
    this.state = state;
    this.clock = clock;
    this.finishedSpanHandler = finishedSpanHandler;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public ScopedSpan annotate(String value) {
    state.annotate(clock.currentTimeMicroseconds(), value);
    return this;
  }

  @Override public ScopedSpan tag(String key, String value) {
    state.tag(key, value);
    return this;
  }

  @Override public ScopedSpan error(Throwable throwable) {
    state.error(throwable);
    return this;
  }

  @Override public void finish() {
    scope.close();
    if (!pendingSpans.remove(context)) return; // don't double-report
    state.finishTimestamp(clock.currentTimeMicroseconds());
    finishedSpanHandler.handle(context, state);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof RealScopedSpan)) return false;
    RealScopedSpan that = (RealScopedSpan) o;
    return context.equals(that.context) && scope.equals(that.scope);
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= context.hashCode();
    h *= 1000003;
    h ^= scope.hashCode();
    return h;
  }
}

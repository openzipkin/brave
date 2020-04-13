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
package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.recorder.PendingSpans;
import brave.propagation.TraceContext;

/** This wraps the public api and guards access to a mutable span. */
final class RealSpan extends Span {

  final TraceContext context;
  final PendingSpans pendingSpans;
  final MutableSpan state;
  final Clock clock;
  final FinishedSpanHandler finishedSpanHandler;

  RealSpan(TraceContext context,
    PendingSpans pendingSpans,
    MutableSpan state,
    Clock clock,
    FinishedSpanHandler finishedSpanHandler
  ) {
    this.context = context;
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

  @Override public SpanCustomizer customizer() {
    return new SpanCustomizerShield(this);
  }

  @Override public Span start() {
    return start(clock.currentTimeMicroseconds());
  }

  @Override public Span start(long timestamp) {
    synchronized (state) {
      state.startTimestamp(timestamp);
    }
    return this;
  }

  @Override public Span name(String name) {
    synchronized (state) {
      state.name(name);
    }
    return this;
  }

  @Override public Span kind(Kind kind) {
    synchronized (state) {
      state.kind(kind);
    }
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(clock.currentTimeMicroseconds(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    // Modern instrumentation should not send annotations such as this, but we leniently
    // accept them rather than fail. This for example allows old bridges like to Brave v3 to work
    if ("cs".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.CLIENT);
        state.startTimestamp(timestamp);
      }
    } else if ("sr".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.SERVER);
        state.startTimestamp(timestamp);
      }
    } else if ("cr".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.CLIENT);
      }
      finish(timestamp);
    } else if ("ss".equals(value)) {
      synchronized (state) {
        state.kind(Span.Kind.SERVER);
      }
      finish(timestamp);
    } else {
      synchronized (state) {
        state.annotate(timestamp, value);
      }
    }
    return this;
  }

  @Override public Span tag(String key, String value) {
    synchronized (state) {
      state.tag(key, value);
    }
    return this;
  }

  @Override public Span error(Throwable throwable) {
    synchronized (state) {
      state.error(throwable);
    }
    return this;
  }

  @Override public Span remoteServiceName(String remoteServiceName) {
    synchronized (state) {
      state.remoteServiceName(remoteServiceName);
    }
    return this;
  }

  @Override public boolean remoteIpAndPort(String remoteIp, int remotePort) {
    synchronized (state) {
      return state.remoteIpAndPort(remoteIp, remotePort);
    }
  }

  @Override public void finish() {
    finish(0L);
  }

  @Override public void finish(long timestamp) {
    synchronized (state) {
      if (!pendingSpans.finish(context, timestamp)) return;
    }
    finishedSpanHandler.handle(context, state);
  }

  @Override public void abandon() {
    pendingSpans.abandon(context);
  }

  @Override public void flush() {
    if (!pendingSpans.flush(context)) return;
    finishedSpanHandler.handle(context, state);
  }

  @Override public String toString() {
    return "RealSpan(" + context + ")";
  }

  /**
   * This also matches equals against a lazy span. The rationale is least surprise to the user, as
   * code should not act differently given an instance of lazy or {@link RealSpan}.
   */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return isEqualToRealOrLazySpan(context, o);
  }

  // We don't compare a RealSpan vs a NoopSpan as they can never equal each other.
  // RealSpan's are always locally sampled and Noop ones are always not.
  static boolean isEqualToRealOrLazySpan(TraceContext context, Object o) {
    if (o instanceof LazySpan) {
      return context.equals(((LazySpan) o).context);
    } else if (o instanceof RealSpan) {
      return context.equals(((RealSpan) o).context);
    }
    return false;
  }

  @Override public int hashCode() {
    return context.hashCode();
  }
}

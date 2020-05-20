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
import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.collect.WeakConcurrentMap;
import brave.propagation.TraceContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal support class for {@link Tracing.Builder#trackOrphans()}.
 *
 * <p>It is unlikely this can be made non-internal as there's a chicken-egg concern between what's
 * needed to initialize this. For example, this is needed inside the {@link Tracing} constructor,
 * and with the final reference of the internal {@link MutableSpan} it uses as well the final
 * reference of the {@link Clock}. However, this can be used in our integration tests as we can take
 * care to initialize those items carefully.
 */
// not final for tests and to avoid CI related problems with experimental final class mocks
public class OrphanTracker extends SpanHandler {
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    MutableSpan defaultSpan;
    Clock clock;
    Level logLevel = Level.FINE;

    /**
     * When initializing a new span, defaults such as service name are copied. We need to be able to
     * tell if the span at the end hook is default or not. Hence, we need access to the same default
     * data. No default.
     */
    public Builder defaultSpan(MutableSpan defaultSpan) {
      this.defaultSpan = defaultSpan;
      return this;
    }

    /**
     * Only used when a span is orphaned, used to add "brave.flush" annotation with the timestamp it
     * was expunged due to garbage collection. Unless overridden by {@link
     * Tracing.Builder#clock(Clock)}, this will be {@link Platform#clock()}. No default.
     */
    public Builder clock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * {@link Level#FINE} for production (to not fill logs) or {@link Level#WARNING} for unit tests
     * (so bugs can be seen). Default {@link Level#FINE}
     */
    public Builder logLevel(Level logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    public SpanHandler build() {
      if (defaultSpan == null) throw new NullPointerException("defaultSpan == null");
      if (clock == null) throw new NullPointerException("clock == null");
      return new OrphanTracker(this);
    }

    Builder() {
    }
  }

  final MutableSpan defaultSpan;
  final Clock clock;
  final WeakConcurrentMap<MutableSpan, Throwable> spanToCaller = new WeakConcurrentMap<>();
  final Level logLevel;

  OrphanTracker(Builder builder) {
    this.defaultSpan = builder.defaultSpan;
    this.clock = builder.clock;
    this.logLevel = builder.logLevel;
  }

  @Override
  public boolean begin(TraceContext context, MutableSpan span, @Nullable TraceContext parent) {
    Throwable oldCaller = spanToCaller.putIfProbablyAbsent(span,
      new Throwable("Thread " + Thread.currentThread().getName() + " allocated span here"));
    assert oldCaller == null :
      "Bug: unexpected to have an existing reference to a new MutableSpan!";
    return true;
  }

  /**
   * In the case of {@link Cause#ORPHANED}, the calling thread will be an arbitrary invocation of
   * {@link Span} or {@link ScopedSpan} as spans orphaned from GC are expunged inline (not on the GC
   * thread). While this class is used for troubleshooting, it should do the least work possible to
   * prevent harm to arbitrary callers.
   */
  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    Throwable caller = spanToCaller.remove(span);
    if (cause != Cause.ORPHANED) return true;
    boolean allocatedButNotUsed = span.equals(new MutableSpan(context, defaultSpan));
    if (caller != null) log(context, allocatedButNotUsed, caller);
    if (allocatedButNotUsed) return true; // skip adding an annotation
    span.annotate(clock.currentTimeMicroseconds(), "brave.flush");
    return true;
  }

  void log(TraceContext context, boolean allocatedButNotUsed, Throwable caller) {
    Logger logger = logger();
    if (!logger.isLoggable(logLevel)) return;
    String message = allocatedButNotUsed
        ? "Span " + context + " was allocated but never used"
        : "Span " + context + " neither finished nor flushed before GC";
    logger.log(logLevel, message, caller);
  }

  Logger logger() {
    return LoggerHolder.LOG;
  }

  @Override public String toString() {
    return "OrphanTracker{}";
  }

  // Use nested class to ensure logger isn't initialized unless it is accessed once.
  static final class LoggerHolder {
    /** @see Tracing.Builder#trackOrphans() which mentions this logger */
    static final Logger LOG = Logger.getLogger(Tracing.class.getName());
  }
}

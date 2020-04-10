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
package brave.propagation;

import brave.Tracing;
import brave.internal.Nullable;

/**
 * In-process trace context propagation backed by a static thread local.
 *
 * <h3>Design notes</h3>
 *
 * <p>A static thread local ensures we have one context per thread, as opposed to one per thread-
 * tracer. This means all tracer instances will be able to see any tracer's contexts.
 *
 * <p>The trade-off of this (instance-based reference) vs the reverse: trace contexts are not
 * separated by tracer by default. For example, to make a trace invisible to another tracer, you
 * have to use a non-default implementation.
 *
 * <p>Sometimes people make different instances of the tracer just to change configuration like
 * the local service name. If we used a thread-instance approach, none of these would be able to see
 * eachother's scopes. This would break {@link Tracing#currentTracer()} scope visibility in a way
 * few would want to debug. It might be phrased as "MySQL always starts a new trace and I don't know
 * why."
 *
 * <p>If you want a different behavior, use a different subtype of {@link CurrentTraceContext},
 * possibly your own, or raise an issue and explain what your use case is.
 */
public class ThreadLocalCurrentTraceContext extends CurrentTraceContext { // not final for backport
  public static CurrentTraceContext create() {
    return new Builder(DEFAULT).build();
  }

  public static Builder newBuilder() {
    return new Builder(DEFAULT);
  }

  /**
   * This component is backed by a possibly static shared thread local. Call this to clear the
   * reference when you are sure any residual state is due to a leak. This is generally only useful
   * in tests.
   *
   * @since 5.11
   */
  public void clear() {
    local.remove();
  }

  /** @since 5.11 */ // overridden for covariance
  public static final class Builder extends CurrentTraceContext.Builder {
    final ThreadLocal<TraceContext> local;

    Builder(ThreadLocal<TraceContext> local) {
      this.local = local;
    }

    @Override public Builder addScopeDecorator(ScopeDecorator scopeDecorator) {
      return (Builder) super.addScopeDecorator(scopeDecorator);
    }

    @Override public ThreadLocalCurrentTraceContext build() {
      return new ThreadLocalCurrentTraceContext(this);
    }
  }

  static final ThreadLocal<TraceContext> DEFAULT = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: to support multiple Tracer instances
  final ThreadLocal<TraceContext> local;
  final RevertToNullScope revertToNull;

  ThreadLocalCurrentTraceContext(Builder builder) {
    super(builder);
    if (builder.local == null) throw new NullPointerException("local == null");
    local = builder.local;
    revertToNull = new RevertToNullScope(local);
  }

  @Override public TraceContext get() {
    return local.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    final TraceContext previous = local.get();
    local.set(currentSpan);
    Scope result = previous != null ? new RevertToPreviousScope(local, previous) : revertToNull;
    return decorateScope(currentSpan, result);
  }

  static final class RevertToNullScope implements Scope {
    final ThreadLocal<TraceContext> local;

    RevertToNullScope(ThreadLocal<TraceContext> local) {
      this.local = local;
    }

    @Override public void close() {
      local.set(null);
    }
  }

  static final class RevertToPreviousScope implements Scope {
    final ThreadLocal<TraceContext> local;
    final TraceContext previous;

    RevertToPreviousScope(ThreadLocal<TraceContext> local, TraceContext previous) {
      this.local = local;
      this.previous = previous;
    }

    @Override public void close() {
      local.set(previous);
    }
  }
}

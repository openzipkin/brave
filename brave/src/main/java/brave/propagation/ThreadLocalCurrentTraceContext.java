package brave.propagation;

import brave.Tracing;
import brave.internal.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation which is backed by a static thread local.
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

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends CurrentTraceContext.Builder {
    List<ScopeDecorator> scopeDecorators = Collections.emptyList();

    @Override public Builder scopeDecorators(List<ScopeDecorator> scopeDecorators) {
      if (scopeDecorators == null) throw new NullPointerException("scopeDecorators == null");
      this.scopeDecorators = scopeDecorators;
      return this;
    }

    @Override public CurrentTraceContext build() {
      return new ThreadLocalCurrentTraceContext(scopeDecorators, DEFAULT);
    }

    Builder() {
    }
  }

  static final ThreadLocal<TraceContext> DEFAULT = new ThreadLocal<>();

  final ThreadLocal<TraceContext> local;

  ThreadLocalCurrentTraceContext(
      List<ScopeDecorator> scopeDecorators,
      ThreadLocal<TraceContext> local
  ) {
    super(scopeDecorators);
    if (local == null) throw new NullPointerException("local == null");
    this.local = local;
  }

  @Override public TraceContext get() {
    return local.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    final TraceContext previous = local.get();
    local.set(currentSpan);
    class DefaultCurrentTraceContextScope implements Scope {
      @Override public void close() {
        local.set(previous);
      }
    }
    Scope result = new DefaultCurrentTraceContextScope();
    return decorateScope(currentSpan, result);
  }
}
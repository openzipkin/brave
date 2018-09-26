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
 *
 * <p>This uses a mutable reference to avoid expensive operations implied by {@link ThreadLocal#set(Object)}.
 * Credit to Trask Stalnaker from the FastThreadLocal library in glowroot. One small change is that
 * we don't use an brave-defined holder object as that would prevent class unloading.
 */
public class ThreadLocalCurrentTraceContext extends CurrentTraceContext { // not final for backport
  public static CurrentTraceContext create() {
    return new Builder().build();
  }

  public static CurrentTraceContext.Builder newBuilder() {
    return new Builder();
  }

  static final class Builder extends CurrentTraceContext.Builder{

    @Override public CurrentTraceContext build() {
      return new ThreadLocalCurrentTraceContext(this, DEFAULT);
    }

    Builder() {
    }
  }

  static final ThreadLocal<Object[]> DEFAULT = new ThreadLocal<>();

  @SuppressWarnings("ThreadLocalUsage") // intentional: to support multiple Tracer instances
  final ThreadLocal<Object[]> local;

  ThreadLocalCurrentTraceContext(
      CurrentTraceContext.Builder builder,
      ThreadLocal<Object[]> local
  ) {
    super(builder);
    if (local == null) throw new NullPointerException("local == null");
    this.local = local;
  }

  @Override public TraceContext get() {
    return (TraceContext) currentTraceContext()[0];
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    Object[] ref = currentTraceContext();
    final TraceContext previous = (TraceContext) ref[0];
    ref[0] = currentSpan;
    class ThreadLocalScope implements Scope {
      @Override public void close() {
        ref[0] = previous;
      }
    }
    Scope result = new ThreadLocalScope();
    return decorateScope(currentSpan, result);
  }

  /**
   * This uses an object array to avoid leaking a Brave type onto a thread local. If we used a Brave
   * type, it would prevent unloading Brave classes.
   */
  Object[] currentTraceContext() {
    Object[] ref = local.get();
    if (ref == null) {
      ref = new Object[1];
      local.set(ref);
    }
    return ref;
  }
}

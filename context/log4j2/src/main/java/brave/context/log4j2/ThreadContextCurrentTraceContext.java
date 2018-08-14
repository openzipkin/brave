package brave.context.log4j2;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adds {@linkplain ThreadContext} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * @deprecated use {@linkplain ThreadContextScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class ThreadContextCurrentTraceContext extends CurrentTraceContext {
  static final CurrentTraceContext.Builder SCOPE_DECORATING_BUILDER =
      ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(ThreadContextScopeDecorator.create());

  public static ThreadContextCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static ThreadContextCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new ThreadContextCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  ThreadContextCurrentTraceContext(CurrentTraceContext delegate) {
    super(SCOPE_DECORATING_BUILDER);
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    Scope scope = delegate.newScope(currentSpan);
    return decorateScope(currentSpan, scope);
  }
}

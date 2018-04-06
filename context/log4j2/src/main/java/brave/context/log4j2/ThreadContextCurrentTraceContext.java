package brave.context.log4j2;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.Collections;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adds {@linkplain ThreadContext} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class ThreadContextCurrentTraceContext extends CurrentTraceContext {
  public static ThreadContextCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static ThreadContextCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new ThreadContextCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  ThreadContextCurrentTraceContext(CurrentTraceContext delegate) {
    super(Collections.singletonList(ThreadContextScopeDecorator.INSTANCE));
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

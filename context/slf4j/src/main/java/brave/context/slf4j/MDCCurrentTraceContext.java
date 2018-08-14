package brave.context.slf4j;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * @deprecated use {@linkplain MDCScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class MDCCurrentTraceContext extends CurrentTraceContext {
  static final CurrentTraceContext.Builder SCOPE_DECORATING_BUILDER =
      ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(MDCScopeDecorator.create());

  public static MDCCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static MDCCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new MDCCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  MDCCurrentTraceContext(CurrentTraceContext delegate) {
    super(SCOPE_DECORATING_BUILDER);
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public CurrentTraceContext.Scope newScope(@Nullable TraceContext currentSpan) {
    CurrentTraceContext.Scope scope = delegate.newScope(currentSpan);
    return decorateScope(currentSpan, scope);
  }
}

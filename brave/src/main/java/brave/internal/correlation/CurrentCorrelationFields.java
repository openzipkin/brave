package brave.internal.correlation;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

/**
 * Provides a mechanism for end users to be able to customise the current correlation fields.
 *
 * <p>Handles the case of there being no current span in scope.
 */
public final class CurrentCorrelationFields extends CorrelationFields {

  private final CurrentTraceContext currentTraceContext;

  /** Affects the current span in scope if present */
  public static CurrentCorrelationFields create(Tracing tracing) {
    return new CurrentCorrelationFields(tracing);
  }

  CurrentCorrelationFields(Tracing tracing) {
    this.currentTraceContext = tracing.currentTraceContext();
  }

  /** {@inheritDoc} */
  @Override public boolean isNoop() {
    TraceContext context = currentTraceContext.get();
    return context != null || context.correlationFields().isNoop();
  }

  /** {@inheritDoc} */
  @Override public void set(String name, String value) {
    TraceContext context = currentTraceContext.get();
    if (context == null) return;
    context.correlationFields().set(name, value);
  }

  /** {@inheritDoc} */
  @Override public void setAll(CorrelationFields other) {
    TraceContext context = currentTraceContext.get();
    if (context == null) return;
    context.correlationFields().setAll(other);
  }

  /** {@inheritDoc} */
  @Override public String get(String name) {
    TraceContext context = currentTraceContext.get();
    if (context == null) return null;
    return context.correlationFields().get(name);
  }

  @Override public boolean isEmpty() {
    TraceContext context = currentTraceContext.get();
    if (context == null) return true;
    return context.correlationFields().isEmpty();
  }

  /** {@inheritDoc} */
  @Override public void forEach(Consumer consumer) {
    TraceContext context = currentTraceContext.get();
    if (context == null) return;
    context.correlationFields().forEach(consumer);
  }

  @Override public CorrelationFields clone() {
    return this;
  }
}

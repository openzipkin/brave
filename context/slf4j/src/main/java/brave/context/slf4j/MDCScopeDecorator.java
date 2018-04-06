package brave.context.slf4j;

import brave.internal.propagation.CorrelationFieldScopeDecorator;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class MDCScopeDecorator extends CorrelationFieldScopeDecorator {
  static final MDCScopeDecorator INSTANCE = new MDCScopeDecorator();

  public static MDCScopeDecorator create() {
    return INSTANCE;
  }

  @Override protected String getIfString(String key) {
    return MDC.get(key);
  }

  @Override protected void put(String key, String value) {
    MDC.put(key, value);
  }

  @Override protected void remove(String key) {
    MDC.remove(key);
  }
}

package brave.context.log4j12;

import brave.internal.propagation.CorrelationFieldScopeDecorator;
import org.apache.log4j.MDC;

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
    Object result = MDC.get(key);
    return result instanceof String ? (String) result : null;
  }

  @Override protected void put(String key, String value) {
    MDC.put(key, value);
  }

  @Override protected void remove(String key) {
    MDC.remove(key);
  }
}

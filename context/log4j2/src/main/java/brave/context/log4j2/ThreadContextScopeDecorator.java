package brave.context.log4j2;

import brave.internal.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adds {@linkplain ThreadContext} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class ThreadContextScopeDecorator extends CorrelationFieldScopeDecorator {
  static final ThreadContextScopeDecorator INSTANCE = new ThreadContextScopeDecorator();

  public static ScopeDecorator create() {
    return INSTANCE;
  }

  @Override protected String getIfString(String key) {
    return ThreadContext.get(key);
  }

  @Override protected void put(String key, String value) {
    ThreadContext.put(key, value);
  }

  @Override protected void remove(String key) {
    ThreadContext.remove(key);
  }

  ThreadContextScopeDecorator() {
  }
}

package brave.context.log4j12;

import brave.internal.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.apache.log4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(MDCScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class MDCScopeDecorator extends CorrelationFieldScopeDecorator {
  public static ScopeDecorator create() {
    return new MDCScopeDecorator();
  }

  @Override protected String get(String key) {
    Object result = MDC.get(key);
    return result instanceof String ? (String) result : null;
  }

  @Override protected void put(String key, String value) {
    MDC.put(key, value);
  }

  @Override protected void remove(String key) {
    MDC.remove(key);
  }

  MDCScopeDecorator() {
  }
}

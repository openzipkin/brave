package brave.context.slf4j;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class MDCCurrentTraceContext extends CurrentTraceContext {
  public static MDCCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static MDCCurrentTraceContext create(CurrentTraceContext delegate) {
    return new MDCCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  MDCCurrentTraceContext(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    final String previousTraceId = MDC.get("traceId");
    final String previousParentId = MDC.get("parentId");
    final String previousSpanId = MDC.get("spanId");

    if (currentSpan != null) {
      MDC.put("traceId", currentSpan.traceIdString());
      replace("parentId",
          currentSpan.parentId() != null ? HexCodec.toLowerHex(currentSpan.parentId()) : null);
      MDC.put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
    } else {
      MDC.remove("traceId");
      MDC.remove("spanId");
    }

    Scope scope = delegate.newScope(currentSpan);
    class MDCCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        replace("traceId", previousTraceId);
        replace("parentId", previousParentId);
        replace("spanId", previousSpanId);
      }
    }
    return new MDCCurrentTraceContextScope();
  }

  static void replace(String key, @Nullable String value) {
    if (value != null) {
      MDC.put(key, value);
    } else {
      MDC.remove(key);
    }
  }
}

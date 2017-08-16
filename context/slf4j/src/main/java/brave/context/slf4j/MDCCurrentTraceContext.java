package brave.context.slf4j;

import brave.internal.HexCodec;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId" and "spanId" when a {@link brave.Tracer#currentSpan()
 * span is current}. These can be used in log correlation.
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

  @Override public Scope newScope(TraceContext currentSpan) {
    final String previousTraceId = MDC.get("traceId");
    final String previousSpanId = MDC.get("spanId");

    if (currentSpan != null) {
      MDC.put("traceId", currentSpan.traceIdString());
      MDC.put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
    } else {
      MDC.remove("traceId");
      MDC.remove("spanId");
    }

    Scope scope = delegate.newScope(currentSpan);
    class MDCCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        MDC.put("traceId", previousTraceId);
        MDC.put("spanId", previousSpanId);
      }
    }
    return new MDCCurrentTraceContextScope();
  }
}

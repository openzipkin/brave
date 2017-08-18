package brave.context.log4j2;

import brave.internal.HexCodec;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adds {@linkplain ThreadContext} properties "traceId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class ThreadContextCurrentTraceContext extends CurrentTraceContext {
  public static ThreadContextCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static ThreadContextCurrentTraceContext create(CurrentTraceContext delegate) {
    return new ThreadContextCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  ThreadContextCurrentTraceContext(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(TraceContext currentSpan) {
    final String previousTraceId = ThreadContext.get("traceId");
    final String previousSpanId = ThreadContext.get("spanId");

    if (currentSpan != null) {
      ThreadContext.put("traceId", currentSpan.traceIdString());
      ThreadContext.put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
    } else {
      ThreadContext.remove("traceId");
      ThreadContext.remove("spanId");
    }

    Scope scope = delegate.newScope(currentSpan);
    class ThreadContextCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        ThreadContext.put("traceId", previousTraceId);
        ThreadContext.put("spanId", previousSpanId);
      }
    }
    return new ThreadContextCurrentTraceContextScope();
  }
}

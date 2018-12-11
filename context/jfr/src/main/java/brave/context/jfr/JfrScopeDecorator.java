package brave.context.jfr;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * Adds {@linkplain Event} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used to correlate JDK Flight recorder
 * events with logs or Zipkin data correlation.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(JfrScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class JfrScopeDecorator implements ScopeDecorator {

  @Category("Zipkin")
  @Label("Scope Event")
  @Description("Zipkin event representing a span being placed in scope")
  static final class ScopeEvent extends Event {
    @Label("Trace Id") String traceId;
    @Label("Parent Span Id") String parentSpanId;
    @Label("Span Id") String spanId;
  }

  public static ScopeDecorator create() {
    return new JfrScopeDecorator();
  }

  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    ScopeEvent event = new ScopeEvent();
    if (!event.isEnabled()) return scope;

    if (currentSpan != null) {
      event.traceId = currentSpan.traceIdString();
      long parentId = currentSpan.parentIdAsLong();
      if (parentId != 0L) event.parentSpanId = HexCodec.toLowerHex(parentId);
      event.spanId = HexCodec.toLowerHex(currentSpan.spanId());
    }

    event.begin();

    class JfrCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        if (!event.shouldCommit()) return;
        event.end();
        event.commit();
      }
    }
    return new JfrCurrentTraceContextScope();
  }

  JfrScopeDecorator() {
  }
}

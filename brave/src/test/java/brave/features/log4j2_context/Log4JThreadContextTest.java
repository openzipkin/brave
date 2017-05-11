package brave.features.log4j2_context;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4JThreadContextTest {

  /**
   * One common request is to support SLF4J or Log4J2 log correlation. This shows you can make a
   * custom implementation
   */
  @Test public void customCurrentTraceContext() {
    assertThat(ThreadContext.get("traceID"))
        .isNull();

    Tracer tracer = Tracing.newBuilder()
        .currentTraceContext(new Log4J2CurrentTraceContext()).build().tracer();

    Span parent = tracer.newTrace();
    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      // the trace id is now in the logging context
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(parent.context().traceIdString());

      // Clear a scope temporarily
      try (Tracer.SpanInScope noScope = tracer.withSpanInScope(null)) {
        assertThat(tracer.currentSpan())
            .isNull();
      }

      Span child = tracer.newChild(parent.context());
      try (Tracer.SpanInScope wsChild = tracer.withSpanInScope(child)) {
        // nesting worked
        assertThat(ThreadContext.get("traceId"))
            .isEqualTo(child.context().traceIdString());
      }

      // old parent reverted
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(parent.context().traceIdString());
    }
    assertThat(ThreadContext.get("traceId"))
        .isNull();
  }

  static class Log4J2CurrentTraceContext extends CurrentTraceContext {
    CurrentTraceContext delegate = new CurrentTraceContext.Default();

    @Override public TraceContext get() {
      return delegate.get();
    }

    @Override public Scope newScope(TraceContext currentSpan) {
      final String previousTraceId = ThreadContext.get("traceId");
      if (currentSpan != null) {
        ThreadContext.put("traceId", currentSpan.traceIdString());
      } else {
        ThreadContext.remove("traceId");
      }
      Scope scope = delegate.newScope(currentSpan);
      return () -> {
        scope.close();
        ThreadContext.put("traceId", previousTraceId);
      };
    }
  }
}

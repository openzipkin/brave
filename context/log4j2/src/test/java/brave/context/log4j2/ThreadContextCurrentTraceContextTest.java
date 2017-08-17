package brave.context.log4j2;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.HexCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadContextCurrentTraceContextTest {
  Logger testLogger = LogManager.getLogger();

  @Test public void testSpan() {
    assertThat(ThreadContext.get("traceId"))
        .isNull();
    assertThat(ThreadContext.get("spanId"))
        .isNull();
    testLogger.info("no span");

    Tracer tracer = Tracing.newBuilder()
        .currentTraceContext(ThreadContextCurrentTraceContext.create())
        .build().tracer();

    Span parent = tracer.newTrace();
    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      testLogger.info("with span: " + parent);

      // the trace id is now in the logging context
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(parent.context().traceIdString());
      assertThat(ThreadContext.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));

      try (Tracer.SpanInScope noSpan = tracer.withSpanInScope(null)) {
        noSpan.toString(); // make sure it doesn't crash
        testLogger.info("with no span");

        assertThat(ThreadContext.get("traceId"))
            .isNull();
        assertThat(ThreadContext.get("spanId"))
            .isNull();
      }

      Span child = tracer.newChild(parent.context());
      try (Tracer.SpanInScope wsChild = tracer.withSpanInScope(child)) {
        testLogger.info("with span: " + child);

        // nesting worked
        assertThat(ThreadContext.get("traceId"))
            .isEqualTo(child.context().traceIdString());
        assertThat(ThreadContext.get("spanId"))
            .isEqualTo(HexCodec.toLowerHex(child.context().spanId()));
      }
      testLogger.info("with span: " + parent);

      // old parent reverted
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(parent.context().traceIdString());
      assertThat(ThreadContext.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));
    }
    testLogger.info("no span");

    assertThat(ThreadContext.get("traceId"))
        .isNull();
    assertThat(ThreadContext.get("spanId"))
        .isNull();

    Tracing.current().close();
  }
}

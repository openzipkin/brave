package brave.context.slf4j;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.HexCodec;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCCurrentTraceContextTest {
  Logger testLogger = LoggerFactory.getLogger(getClass());

  @Test public void test() {
    assertThat(MDC.get("traceId"))
        .isNull();
    assertThat(MDC.get("spanId"))
        .isNull();
    testLogger.info("no span");

    Tracer tracer = Tracing.newBuilder()
        .currentTraceContext(MDCCurrentTraceContext.create())
        .build().tracer();

    Span parent = tracer.newTrace();
    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      testLogger.info("with span: " + parent);

      try (Tracer.SpanInScope noSpan = tracer.withSpanInScope(null)) {
        noSpan.toString(); // make sure it doesn't crash
        testLogger.info("with no span");

        assertThat(ThreadContext.get("traceId"))
            .isNull();
        assertThat(ThreadContext.get("spanId"))
            .isNull();
      }

      // the trace id is now in the logging context
      assertThat(MDC.get("traceId"))
          .isEqualTo(parent.context().traceIdString());
      assertThat(MDC.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));

      Span child = tracer.newChild(parent.context());
      try (Tracer.SpanInScope wsChild = tracer.withSpanInScope(child)) {
        testLogger.info("with span: " + child);

        // nesting worked
        assertThat(MDC.get("traceId"))
            .isEqualTo(child.context().traceIdString());
        assertThat(MDC.get("spanId"))
            .isEqualTo(HexCodec.toLowerHex(child.context().spanId()));
      }
      testLogger.info("with span: " + parent);

      // old parent reverted
      assertThat(MDC.get("traceId"))
          .isEqualTo(parent.context().traceIdString());
      assertThat(MDC.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));
    }
    testLogger.info("no span");

    assertThat(MDC.get("traceId"))
        .isNull();
    assertThat(MDC.get("spanId"))
        .isNull();

    Tracing.current().close();
  }
}

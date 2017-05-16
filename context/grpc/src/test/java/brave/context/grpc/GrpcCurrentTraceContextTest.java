package brave.context.grpc;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import io.grpc.Context;
import org.junit.Test;

import static brave.context.grpc.GrpcCurrentTraceContext.TRACE_CONTEXT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public class GrpcCurrentTraceContextTest {

  @Test public void grpcBraveInterop() throws Exception {
    Tracer tracer = Tracing.newBuilder()
        .currentTraceContext(GrpcCurrentTraceContext.create()).build().tracer();

    Span parent = tracer.newTrace(); // start a trace in Brave
    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      // Inside the parent scope, trace context is consistent between grpc and brave
      assertThat(tracer.currentSpan().context())
          .isEqualTo(TRACE_CONTEXT_KEY.get(Context.current()));

      // Clear a scope temporarily
      try (Tracer.SpanInScope noScope = tracer.withSpanInScope(null)) {
        assertThat(tracer.currentSpan())
            .isNull();
      }

      Context.ROOT.run(() -> {
        assertThat(tracer.currentSpan())
            .isNull();
      });

      // The parent span was reverted
      assertThat(tracer.currentSpan().context())
          .isEqualTo(TRACE_CONTEXT_KEY.get(Context.current()));
    }

    // Outside a scope, trace context is consistent between grpc and brave
    assertThat(tracer.currentSpan()).isNull();
    assertThat(TRACE_CONTEXT_KEY.get(Context.current())).isNull();
  }
}

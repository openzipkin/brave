package brave.context.log4j2;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextTest;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadContextCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected CurrentTraceContext currentTraceContext() {
    return ThreadContextCurrentTraceContext.create(CurrentTraceContext.Default.create());
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(context.traceIdString());
      assertThat(ThreadContext.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(context.spanId()));
    } else {
      assertThat(ThreadContext.get("traceId"))
          .isNull();
      assertThat(ThreadContext.get("spanId"))
          .isNull();
    }
  }
}

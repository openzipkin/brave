package brave.context.slf4j;

import brave.internal.HexCodec;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContextTest;
import brave.propagation.TraceContext;
import javax.annotation.Nullable;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected CurrentTraceContext currentTraceContext() {
    return MDCCurrentTraceContext.create(CurrentTraceContext.Default.create());
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId"))
          .isEqualTo(context.traceIdString());
      assertThat(MDC.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(context.spanId()));
    } else {
      assertThat(MDC.get("traceId"))
          .isNull();
      assertThat(MDC.get("spanId"))
          .isNull();
    }
  }
}
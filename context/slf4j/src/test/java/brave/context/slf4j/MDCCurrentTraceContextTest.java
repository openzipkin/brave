package brave.context.slf4j;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return MDCCurrentTraceContext.create(CurrentTraceContext.Default.create());
    }
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId"))
          .isEqualTo(context.traceIdString());
      long parentId = context.parentIdAsLong();
      assertThat(MDC.get("parentId"))
          .isEqualTo(parentId != 0L ? HexCodec.toLowerHex(parentId) : null);
      assertThat(MDC.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(context.spanId()));
    } else {
      assertThat(MDC.get("traceId"))
          .isNull();
      assertThat(MDC.get("parentId"))
          .isNull();
      assertThat(MDC.get("spanId"))
          .isNull();
    }
  }
}
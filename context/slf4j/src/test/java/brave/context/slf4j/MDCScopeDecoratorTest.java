package brave.context.slf4j;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

public class MDCScopeDecoratorTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(MDCScopeDecorator.create())
          .build();
    }
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId"))
          .isEqualTo(context.traceIdString());
      assertThat(MDC.get("parentId"))
          .isEqualTo(context.parentIdString());
      assertThat(MDC.get("spanId"))
          .isEqualTo(context.spanIdString());
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
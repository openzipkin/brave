package brave.context.log4j2;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.logging.log4j.ThreadContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadContextScopeDecoratorTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(ThreadContextScopeDecorator.create())
          .build();
    }
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(ThreadContext.get("traceId"))
          .isEqualTo(context.traceIdString());
      assertThat(ThreadContext.get("parentId"))
          .isEqualTo(context.parentIdString());
      assertThat(ThreadContext.get("spanId"))
          .isEqualTo(context.spanIdString());
    } else {
      assertThat(ThreadContext.get("traceId"))
          .isNull();
      assertThat(ThreadContext.get("parentId"))
          .isNull();
      assertThat(ThreadContext.get("spanId"))
          .isNull();
    }
  }
}

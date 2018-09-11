package brave.context.log4j2;

import brave.internal.HexCodec;
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
      long parentId = context.parentIdAsLong();
      assertThat(ThreadContext.get("parentId"))
          .isEqualTo(parentId != 0L ? HexCodec.toLowerHex(parentId) : null);
      assertThat(ThreadContext.get("spanId"))
          .isEqualTo(HexCodec.toLowerHex(context.spanId()));
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

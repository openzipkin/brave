package brave.context.log4j12;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.log4j.MDC;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static brave.context.log4j12.MDCScopeDecoratorTest.assumeMDCWorks;
import static org.assertj.core.api.Assertions.assertThat;

public class MDCCurrentTraceContextTest extends CurrentTraceContextTest {

  public MDCCurrentTraceContextTest() {
    assumeMDCWorks();
  }

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return MDCCurrentTraceContext.create();
    }
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(currentTraceContext);
  }

  @Test(expected = ComparisonFailure.class) // Log4J 1.2.x MDC is inheritable by default
  public void isnt_inheritable() throws Exception {
    super.isnt_inheritable();
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
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


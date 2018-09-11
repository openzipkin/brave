package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;

public class ThreadLocalCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return ThreadLocalCurrentTraceContext.newBuilder().build();
    }
  }
}

package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;

public class DefaultCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return CurrentTraceContext.Default.create();
    }
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(CurrentTraceContext.Default.inheritable());
  }

  @Before public void ensureNoOtherTestsTaint() {
    CurrentTraceContext.Default.INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}

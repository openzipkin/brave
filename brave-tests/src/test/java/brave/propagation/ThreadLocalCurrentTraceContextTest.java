package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import org.junit.Before;
import org.junit.Test;

public class ThreadLocalCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected CurrentTraceContext newCurrentTraceContext() {
    return CurrentTraceContext.Default.create();
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(CurrentTraceContext.Default.inheritable());
  }

  @Before public void ensureNoOtherTestsTaint() {
    CurrentTraceContext.Default.INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}

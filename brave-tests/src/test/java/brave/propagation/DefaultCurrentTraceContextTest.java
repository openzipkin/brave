package brave.propagation;

import org.junit.Before;
import org.junit.Test;

public class DefaultCurrentTraceContextTest extends CurrentTraceContextTest {
  CurrentTraceContext currentTraceContext = CurrentTraceContext.Default.create();

  @Override protected CurrentTraceContext currentTraceContext() {
    return currentTraceContext;
  }

  @Test public void is_inheritable() throws Exception {
    super.is_inheritable(CurrentTraceContext.Default.inheritable());
  }

  @Before public void ensureNoOtherTestsTaint() {
    CurrentTraceContext.Default.INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}

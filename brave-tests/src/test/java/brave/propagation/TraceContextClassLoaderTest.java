package brave.propagation;

import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class TraceContextClassLoaderTest {

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      TraceContext.newBuilder().traceId(1).spanId(2).build();
    }
  }
}

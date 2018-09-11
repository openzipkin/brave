package brave;

import org.junit.Test;
import zipkin2.Span;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class TracerClassLoaderTest {
  @Test public void unloadable_withLoggingReporter() {
    assertRunIsUnloadable(UsingLoggingReporter.class, getClass().getClassLoader());
  }

  static class UsingLoggingReporter implements Runnable {
    @Override public void run() {
      Tracing.LoggingReporter reporter = new Tracing.LoggingReporter();
      reporter.report(Span.newBuilder().traceId("a").id("b").build());
    }
  }
}

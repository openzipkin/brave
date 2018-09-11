package brave.propagation;

import brave.Tracing;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class ThreadLocalSpanClassLoaderTest {

  @Test public void noop_unloadable() {
    assertRunIsUnloadable(CurrentTracerUnassigned.class, getClass().getClassLoader());
  }

  static class CurrentTracerUnassigned implements Runnable {
    @Override public void run() {
      ThreadLocalSpan.CURRENT_TRACER.next();
    }
  }

  @Test public void currentTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(ExplicitTracerBasicUsage.class, getClass().getClassLoader());
  }

  static class ExplicitTracerBasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
        ThreadLocalSpan tlSpan = ThreadLocalSpan.create(tracing.tracer());

        tlSpan.next();
        tlSpan.remove().finish();
      }
    }
  }

  @Test public void explicitTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(CurrentTracerBasicUsage.class, getClass().getClassLoader());
  }

  static class CurrentTracerBasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
        ThreadLocalSpan tlSpan = ThreadLocalSpan.CURRENT_TRACER;

        tlSpan.next();
        tlSpan.remove().finish();
      }
    }
  }

  /**
   * TODO: While it is an instrumentation bug to not complete a thread-local span, we should be
   * tolerant, for example considering weak references or similar.
   */
  @Test(expected = AssertionError.class) public void unfinishedSpan_preventsUnloading() {
    assertRunIsUnloadable(CurrentTracerDoesntFinishSpan.class, getClass().getClassLoader());
  }

  static class CurrentTracerDoesntFinishSpan implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
        ThreadLocalSpan.CURRENT_TRACER.next();
      }
    }
  }
}

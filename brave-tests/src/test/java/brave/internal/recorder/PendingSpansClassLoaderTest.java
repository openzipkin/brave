package brave.internal.recorder;

import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class PendingSpansClassLoaderTest {

  @Test public void unloadable_afterCreateAndRemove() {
    assertRunIsUnloadable(CreateAndRemove.class, getClass().getClassLoader());
  }

  static class CreateAndRemove implements Runnable {
    @Override public void run() {
      MutableSpanConverter converter = new MutableSpanConverter("unknown", "127.0.0.1", 0);
      SpanReporter reporter = new SpanReporter(converter, Reporter.NOOP, new AtomicBoolean());
      PendingSpans pendingSpans = new PendingSpans(Platform.get().clock(), reporter, reporter.noop);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      pendingSpans.getOrCreate(context, true);
      pendingSpans.remove(context);
    }
  }

  @Test public void unloadable_afterErrorReporting() {
    assertRunIsUnloadable(ErrorReporting.class, getClass().getClassLoader());
  }

  static class ErrorReporting implements Runnable {
    @Override public void run() {
      MutableSpanConverter converter = new MutableSpanConverter("unknown", "127.0.0.1", 0);
      SpanReporter reporter = new SpanReporter(converter, s -> {
        throw new RuntimeException();
      }, new AtomicBoolean());
      PendingSpans pendingSpans = new PendingSpans(Platform.get().clock(), reporter, reporter.noop);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      pendingSpans.getOrCreate(context, true);
      context = null; // orphan the context

      try {
        blockOnGC();
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }

      pendingSpans.reportOrphanedSpans();
    }
  }

  static void blockOnGC() throws InterruptedException {
    System.gc();
    Thread.sleep(200L);
  }
}

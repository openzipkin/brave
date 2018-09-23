package brave.internal.recorder;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class PendingSpansClassLoaderTest {

  @Test public void unloadable_afterCreateAndRemove() {
    assertRunIsUnloadable(CreateAndRemove.class, getClass().getClassLoader());
  }

  static class CreateAndRemove implements Runnable {
    @Override public void run() {
      FirehoseDispatcher firehoseDispatcher = new FirehoseDispatcher(new FirehoseHandler.Factory() {
        @Override public FirehoseHandler create(String serviceName, String ip, int port) {
          return FirehoseHandler.NOOP;
        }
      }, new ErrorParser(), Reporter.NOOP, "favistar", "1.2.3.4", 0);
      PendingSpans pendingSpans = new PendingSpans(Platform.get().clock(), firehoseDispatcher);

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
      FirehoseDispatcher firehoseDispatcher = new FirehoseDispatcher(new FirehoseHandler.Factory() {
        @Override public FirehoseHandler create(String serviceName, String ip, int port) {
          return FirehoseHandler.NOOP;
        }
      }, new ErrorParser(), s -> {
        throw new RuntimeException();
      }, "favistar", "1.2.3.4", 0);
      PendingSpans pendingSpans = new PendingSpans(Platform.get().clock(), firehoseDispatcher);

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

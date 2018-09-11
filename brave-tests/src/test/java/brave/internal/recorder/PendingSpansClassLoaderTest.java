package brave.internal.recorder;

import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class PendingSpansClassLoaderTest {

  @Test public void unloadable_afterCreateAndRemove() {
    assertRunIsUnloadable(CreateAndRemove.class, getClass().getClassLoader());
  }

  static class CreateAndRemove implements Runnable {
    @Override public void run() {
      PendingSpans pendingSpans = new PendingSpans(
          Endpoint.newBuilder().serviceName("unknown").build(),
          Platform.get().clock(),
          Reporter.NOOP,
          new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      pendingSpans.getOrCreate(context, true);
      pendingSpans.remove(context);
    }
  }
}

package brave.context.rxjava2;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking.SavedHooks;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class CurrentTraceContextAssemblyTrackingClassLoaderTest {

  @Test public void noop_unloadable() {
    assertRunIsUnloadable(Noop.class, getClass().getClassLoader());
  }

  static class Noop implements Runnable {
    @Override public void run() {
      new CurrentTraceContextAssemblyTracking(
          ThreadLocalCurrentTraceContext.newBuilder().build()
      ).enable();
      CurrentTraceContextAssemblyTracking.disable();
    }
  }

  /** Proves when code is correct, we can unload our classes. */
  @Test public void simpleUsage_unloadable() {
    assertRunIsUnloadable(SimpleUsable.class, getClass().getClassLoader());
  }

  static class SimpleUsable implements Runnable {
    @Override public void run() {
      CurrentTraceContext currentTraceContext =
          ThreadLocalCurrentTraceContext.newBuilder().build();
      SavedHooks saved = new CurrentTraceContextAssemblyTracking(currentTraceContext)
          .enableAndChain();

      TraceContext assembly = TraceContext.newBuilder().traceId(1).spanId(1).build();

      try (Scope scope = currentTraceContext.newScope(assembly)) {
        Observable.just(1).map(i -> i).test().assertNoErrors();
      } finally {
        saved.restore();
      }
    }
  }
}

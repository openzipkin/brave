package brave.context.rxjava2;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking.SavedHooks;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import hu.akarnokd.rxjava2.debug.RxJavaAssemblyException;
import hu.akarnokd.rxjava2.debug.RxJavaAssemblyTracking;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentTraceContextAssemblyTrackingTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build();
  TraceContext assemblyContext = TraceContext.newBuilder().traceId(1L).spanId(1L).build();

  @Before public void setup() {
    RxJavaPlugins.reset();
    CurrentTraceContextAssemblyTracking.create(currentTraceContext).enable();
  }

  @After public void tearDown() {
    CurrentTraceContextAssemblyTracking.disable();
  }

  /** This spot-checks Observable to ensure that our chaining works properly. */
  @Test public void enableAndChain_runsOldHooks() {
    RxJavaPlugins.reset();

    // Sanity check that RxJavaAssemblyTracking is not already enabled
    TestObserver<Integer> to = newObservableThatErrs().test()
        .assertFailure(IOException.class, 1, 2, 3, 4, 5);

    assertThat(RxJavaAssemblyException.find(to.errors().get(0))).isNull();

    RxJavaAssemblyTracking.enable();
    try {

      SavedHooks h =
          CurrentTraceContextAssemblyTracking.create(currentTraceContext).enableAndChain();

      to = newObservableThatErrs().test().assertFailure(IOException.class, 1, 2, 3, 4, 5);

      // Old hooks run when there is no current trace context.
      Throwable error = to.errors().get(0);
      assertThat(error).hasMessage(null); // our hook didn't run as there was no trace context
      assertThat(RxJavaAssemblyException.find(error)).isNotNull(); // old hook ran

      try (Scope scope = currentTraceContext.newScope(assemblyContext)) {
        to = newObservableThatErrs().test().assertFailure(IOException.class, 1, 2, 3, 4, 5);
      }

      // Our hook and the old hooks run when there is a current trace context.
      error = to.errors().get(0);
      assertThat(error).hasMessage(assemblyContext.traceIdString()); // our hook ran
      assertThat(RxJavaAssemblyException.find(error)).isNotNull(); // old hook ran

      h.restore();
    } finally {
      RxJavaAssemblyTracking.disable();
    }
  }

  /** If we have a span in scope, the message will be the current trace ID */
  Observable<Integer> newObservableThatErrs() {
    return Observable.range(1, 5).concatWith(Observable.fromCallable(() -> {
      TraceContext ctx = currentTraceContext.get();
      String message = ctx != null ? ctx.traceIdString() : null;
      throw new IOException(message);
    }));
  }

  @Test public void enableAndChain_restoresSavedHooks() {
    RxJavaPlugins.reset();

    RxJavaAssemblyTracking.enable();
    try {
      Object o1 = RxJavaPlugins.getOnCompletableAssembly();
      Object o2 = RxJavaPlugins.getOnSingleAssembly();
      Object o3 = RxJavaPlugins.getOnMaybeAssembly();
      Object o4 = RxJavaPlugins.getOnObservableAssembly();
      Object o5 = RxJavaPlugins.getOnFlowableAssembly();
      Object o6 = RxJavaPlugins.getOnConnectableFlowableAssembly();
      Object o7 = RxJavaPlugins.getOnConnectableObservableAssembly();
      Object o8 = RxJavaPlugins.getOnParallelAssembly();

      SavedHooks h =
          CurrentTraceContextAssemblyTracking.create(currentTraceContext).enableAndChain();

      h.restore();

      Assert.assertSame(o1, RxJavaPlugins.getOnCompletableAssembly());
      Assert.assertSame(o2, RxJavaPlugins.getOnSingleAssembly());
      Assert.assertSame(o3, RxJavaPlugins.getOnMaybeAssembly());
      Assert.assertSame(o4, RxJavaPlugins.getOnObservableAssembly());
      Assert.assertSame(o5, RxJavaPlugins.getOnFlowableAssembly());
      Assert.assertSame(o6, RxJavaPlugins.getOnConnectableFlowableAssembly());
      Assert.assertSame(o7, RxJavaPlugins.getOnConnectableObservableAssembly());
      Assert.assertSame(o8, RxJavaPlugins.getOnParallelAssembly());
    } finally {
      RxJavaAssemblyTracking.disable();
    }
  }

  @Test public void enable_restoresSavedHooks() {
    RxJavaPlugins.reset();

    RxJavaAssemblyTracking.enable();
    try {
      CurrentTraceContextAssemblyTracking.create(currentTraceContext).enable();

      CurrentTraceContextAssemblyTracking.disable();

      Assert.assertNull(RxJavaPlugins.getOnCompletableAssembly());
      Assert.assertNull(RxJavaPlugins.getOnSingleAssembly());
      Assert.assertNull(RxJavaPlugins.getOnMaybeAssembly());
      Assert.assertNull(RxJavaPlugins.getOnObservableAssembly());
      Assert.assertNull(RxJavaPlugins.getOnFlowableAssembly());
      Assert.assertNull(RxJavaPlugins.getOnConnectableFlowableAssembly());
      Assert.assertNull(RxJavaPlugins.getOnConnectableObservableAssembly());
      Assert.assertNull(RxJavaPlugins.getOnParallelAssembly());
    } finally {
      RxJavaAssemblyTracking.disable();
    }
  }
}

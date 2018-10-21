package brave.context.rxjava2;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking.SavedHooks;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import hu.akarnokd.rxjava2.debug.RxJavaAssemblyException;
import hu.akarnokd.rxjava2.debug.RxJavaAssemblyTracking;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.TestSubscriber;
import java.io.IOException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentTraceContextAssemblyTrackingTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build();
  CurrentTraceContext throwingCurrentTraceContext = new CurrentTraceContext() {
    @Override public TraceContext get() {
      return subscribeContext;
    }

    @Override public Scope newScope(TraceContext currentSpan) {
      throw new AssertionError();
    }
  };
  TraceContext assemblyContext = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  TraceContext subscribeContext = assemblyContext.toBuilder().parentId(1L).spanId(2L).build();
  Predicate<Integer> lessThanThreeInAssemblyContext = i -> {
    assertInAssemblyContext();
    return i < 3;
  };
  Predicate<Integer> lessThanThreeInSubscribeContext = i -> {
    assertInSubscribeContext();
    return i < 3;
  };

  @Before public void setup() {
    RxJavaPlugins.reset();
    CurrentTraceContextAssemblyTracking.create(currentTraceContext).enable();
  }

  @After public void tearDown() {
    CurrentTraceContextAssemblyTracking.disable();
  }

  /**
   * This is an example of "conditional micro fusion" where use use a source that supports fusion:
   * {@link Flowable#range(int, int)} with an intermediate operator which supports transitive
   * fusion: {@link Flowable#filter(Predicate)}.
   *
   * <p>We are looking for the assembly trace context to be visible, but specifically inside
   * {@link ConditionalSubscriber#tryOnNext(Object)}, as if we wired things correctly, this will be
   * called instead of {@link Subscriber#onNext(Object)}.
   */
  @Test public void fuseable_filter() {
    Flowable<Integer> fuseable;
    try (Scope scope1 = currentTraceContext.newScope(assemblyContext)) {
      // we want the fitering to occur in the assembly context
      fuseable = Flowable.range(1, 3).filter(i -> {
        assertInAssemblyContext();
        return i < 3;
      });
    }

    ConditionalTestSubscriber<Integer> testSubscriber = new ConditionalTestSubscriber<>();
    try (Scope scope2 = currentTraceContext.newScope(subscribeContext)) {
      // subscribing in a different scope shouldn't affect the assembly context
      fuseable.subscribe(testSubscriber);
    }

    testSubscriber.assertValues(1, 2).assertNoErrors();
  }

  /** This ensures we don't accidentally think we tested tryOnNext */
  class ConditionalTestSubscriber<T> extends TestSubscriber<T> implements ConditionalSubscriber<T> {

    @Override public boolean tryOnNext(T value) {
      super.onNext(value);
      return true;
    }

    @Override public void onNext(T value) {
      throw new AssertionError("unexpected call to onNext: check assumptions");
    }
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

  void assertInAssemblyContext() {
    assertThat(currentTraceContext.get()).isEqualTo(assemblyContext);
  }

  void assertInSubscribeContext() {
    assertThat(currentTraceContext.get()).isEqualTo(subscribeContext);
  }
}

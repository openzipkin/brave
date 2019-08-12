/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.fuseable.ScalarCallable;
import io.reactivex.internal.operators.flowable.FlowableScalarXMap;
import io.reactivex.internal.operators.observable.ObservableScalarXMap;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class NotYetSupportedTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(StrictScopeDecorator.create())
    .build();
  TraceContext assemblyContext = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  TraceContext subscribeContext = assemblyContext.toBuilder().parentId(1L).spanId(2L).build();

  @Before public void setup() {
    RxJavaPlugins.reset();
    CurrentTraceContextAssemblyTracking.create(currentTraceContext).enable();
  }

  @After public void tearDown() {
    CurrentTraceContextAssemblyTracking.disable();
  }

  /**
   * On XMap (ex {@code just(1).concatMap(..}, the source scalar callable is not passed as an input
   * to the subsequent operator like {@code ObservableScalarXMap.ScalarXMapObservable}. What is
   * passed is the result of {@link ScalarCallable#call()}.
   *
   * <p>Usually, this would result in lost tracking of the assembled context. However, we use a
   * thread local to stash the context between {@link ScalarCallable#call()} and the next {@link
   * RxJavaPlugins#onAssembly assembly hook}.
   *
   * @see ObservableScalarXMap#scalarXMap - references to this are operators which require stashing
   */
  @Test(expected = AssertionError.class)
  public void observable_scalarCallable_propagatesContextOnXMap() {
    Observable<Integer> fuseable;
    try (Scope scope1 = currentTraceContext.newScope(assemblyContext)) {
      fuseable = Observable.just(1);
      assertThat(fuseable).isInstanceOf(ScalarCallable.class);
    }

    // eventhough upstream is assembled with XMap, we still inherit the fused context.
    fuseable = fuseable.concatMap(Observable::just);

    assertXMapFusion(fuseable).test().assertValues(1).assertNoErrors();
  }

  /**
   * Same as {@link #observable_scalarCallable_propagatesContextOnXMap()}, except for Flowable.
   *
   * @see FlowableScalarXMap#scalarXMap - references of this will break when assembly
   */
  @Test(expected = AssertionError.class)
  public void flowable_scalarCallable_propagatesContextOnXMap() {
    Observable<Integer> fuseable;
    try (Scope scope1 = currentTraceContext.newScope(assemblyContext)) {
      fuseable = Observable.just(1);
      assertThat(fuseable).isInstanceOf(ScalarCallable.class);
    }

    // eventhough upstream is assembled with XMap, we still inherit the fused context.
    fuseable = fuseable.concatMap(Observable::just);

    assertXMapFusion(fuseable).test().assertValues(1).assertNoErrors();
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
  @Test(expected = AssertionError.class)
  public void conditionalMicroFusion() {
    Flowable<Integer> fuseable;
    try (Scope scope1 = currentTraceContext.newScope(assemblyContext)) {
      // we want the fitering to occur in the assembly context
      fuseable = Flowable.just(1);
      assertThat(fuseable).isInstanceOf(ScalarCallable.class);
    }

    // proves the assembly context is retained even after it is no longer in scope
    // TODO: this lies as if you debug this you'll notice it isn't fusing with upstream
    fuseable = fuseable.filter(i -> {
      assertInAssemblyContext();
      return i < 3;
    });

    ConditionalTestSubscriber<Integer> testSubscriber = new ConditionalTestSubscriber<>();
    try (Scope scope2 = currentTraceContext.newScope(subscribeContext)) {
      // subscribing in a different scope shouldn't affect the assembly context
      fuseable.subscribe(testSubscriber);
    }

    testSubscriber.assertValues(1).assertNoErrors();
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

  Observable<Integer> assertXMapFusion(Observable<Integer> fuseable) {
    return fuseable
      // prove XMap fusion occurred
      .doOnSubscribe(d -> {
        assertThat(d).isInstanceOf(ObservableScalarXMap.ScalarDisposable.class);
      })
      .doOnNext(e -> assertInAssemblyContext())
      .doOnComplete(this::assertInAssemblyContext);
  }

  void assertInAssemblyContext() {
    assertThat(currentTraceContext.get()).isEqualTo(assemblyContext);
  }
}

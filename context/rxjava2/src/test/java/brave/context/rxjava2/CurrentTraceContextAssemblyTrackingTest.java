package brave.context.rxjava2;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking.SavedHooks;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import hu.akarnokd.rxjava2.debug.RxJavaAssemblyTracking;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.fuseable.ScalarCallable;
import io.reactivex.internal.operators.completable.CompletableEmpty;
import io.reactivex.internal.operators.completable.CompletableFromCallable;
import io.reactivex.internal.operators.flowable.FlowableFromCallable;
import io.reactivex.internal.operators.flowable.FlowablePublish;
import io.reactivex.internal.operators.flowable.FlowableRange;
import io.reactivex.internal.operators.maybe.MaybeFromCallable;
import io.reactivex.internal.operators.maybe.MaybeJust;
import io.reactivex.internal.operators.observable.ObservableFromCallable;
import io.reactivex.internal.operators.observable.ObservablePublish;
import io.reactivex.internal.operators.observable.ObservableRange;
import io.reactivex.internal.operators.parallel.ParallelFromPublisher;
import io.reactivex.internal.operators.single.SingleFromCallable;
import io.reactivex.internal.operators.single.SingleJust;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.observers.TestObserver;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.TestSubscriber;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class CurrentTraceContextAssemblyTrackingTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build();
  CurrentTraceContextAssemblyTracking contextTracking =
      CurrentTraceContextAssemblyTracking.create(currentTraceContext);
  TraceContext context1 = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  TraceContext context2 = context1.toBuilder().parentId(1L).spanId(2L).build();
  Predicate<Integer> lessThanThreeInContext1 =
      i -> {
        assertContext1();
        return i < 3;
      };
  Predicate<Integer> lessThanThreeInContext2 =
      i -> {
        assertContext2();
        return i < 3;
      };

  @Before
  public void setup() {
    RxJavaPlugins.reset();
    contextTracking.enable();
  }

  @After
  public void tearDown() {
    contextTracking.disable();
  }

  @Test
  public void completable() {
    Completable source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source = Completable.complete().doOnComplete(() -> assertContext1());
      errorSource =
          Completable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult();
  }

  @Test
  public void completable_subscribesUnderScope() {
    Completable source = Completable.complete().doOnComplete(() -> assertContext2());
    Completable errorSource =
        Completable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult();
  }

  @Test
  public void completable_unwrappedWhenNotInScope() {
    assertThat(Completable.complete()).isEqualTo(CompletableEmpty.INSTANCE);
  }

  @Test
  public void flowable() {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3).doOnNext(i -> assertContext1()).doOnComplete(() -> assertContext1());
      errorSource =
          Flowable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1, 2, 3);
  }

  @Test
  public void flowable_subscribesUnderScope() {
    Flowable<Integer> source =
        Flowable.range(1, 3).doOnNext(i -> assertContext2()).doOnComplete(() -> assertContext2());
    Flowable<Integer> errorSource =
        Flowable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1, 2, 3);
  }

  @Test
  public void flowable_unwrappedWhenNotInScope() {
    assertThat(Flowable.range(1, 3))
        .isInstanceOf(FlowableRange.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void flowable_conditional() {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3)
              .filter(lessThanThreeInContext1)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1());
      errorSource =
          Flowable.<Integer>error(new IllegalStateException())
              .filter(lessThanThreeInContext1)
              .doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1, 2);
  }

  @Test
  public void flowable_conditional_subscribesUnderScope() {
    Flowable<Integer> source =
        Flowable.range(1, 3)
            .filter(lessThanThreeInContext2)
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2());
    Flowable<Integer> errorSource =
        Flowable.<Integer>error(new IllegalStateException())
            .filter(lessThanThreeInContext2)
            .doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1, 2);
  }

  @Test
  public void observable() {
    Observable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Observable.range(1, 3)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1());
      errorSource =
          Observable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source, errorSource).assertResult(1, 2, 3);
  }

  @Test
  public void observable_subscribesUnderScope() {
    Observable<Integer> source =
        Observable.range(1, 3).doOnNext(i -> assertContext2()).doOnComplete(() -> assertContext2());
    Observable<Integer> errorSource =
        Observable.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source, errorSource).assertResult(1, 2, 3);
  }

  @Test
  public void observable_unwrappedWhenNotInScope() {
    assertThat(Observable.range(1, 3))
        .isInstanceOf(ObservableRange.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void single() {
    Single<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source = Single.just(1).doOnSuccess(i -> assertContext1());
      errorSource =
          Single.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
  }

  @Test
  public void single_subscribesUnderScope() {
    Single<Integer> source = Single.just(1).doOnSuccess(i -> assertContext2());
    Single<Integer> errorSource =
        Single.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);
  }

  @Test
  public void single_unwrappedWhenNotInScope() {
    assertThat(Single.just(1))
        .isInstanceOf(SingleJust.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void maybe() {
    Maybe<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source = Maybe.just(1).doOnSuccess(i -> assertContext1());
      errorSource =
          Maybe.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
  }

  @Test
  public void maybe_subscribesUnderScope() {
    Maybe<Integer> source = Maybe.just(1).doOnSuccess(i -> assertContext2());
    Maybe<Integer> errorSource =
        Maybe.<Integer>error(new IllegalStateException()).doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);
  }

  @Test
  public void maybe_unwrappedWhenNotInScope() {
    assertThat(Maybe.just(1))
        .isInstanceOf(MaybeJust.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void parallelFlowable() {
    ParallelFlowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3)
              .parallel()
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1());
      errorSource =
          Flowable.concat(
                  Flowable.<Integer>error(new IllegalStateException()),
                  Flowable.<Integer>error(new IllegalStateException()))
              .parallel()
              .doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source, errorSource).assertResult(1, 2, 3);
  }

  @Test
  public void parallelFlowable_subscribesUnderScope() {
    ParallelFlowable<Integer> source =
        Flowable.range(1, 3)
            .parallel()
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2());
    ParallelFlowable<Integer> errorSource =
        Flowable.concat(
                Flowable.<Integer>error(new IllegalStateException()),
                Flowable.<Integer>error(new IllegalStateException()))
            .parallel()
            .doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source, errorSource).assertResult(1, 2, 3);
  }

  @Test
  public void parallelFlowable_unwrappedWhenNotInScope() {
    assertThat(Flowable.range(1, 3).parallel())
        .isInstanceOf(ParallelFromPublisher.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void parallelFlowable_conditional() {
    ParallelFlowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3)
              .parallel()
              .filter(lessThanThreeInContext1)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1());
      errorSource =
          Flowable.concat(
                  Flowable.<Integer>error(new IllegalStateException()),
                  Flowable.<Integer>error(new IllegalStateException()))
              .parallel()
              .filter(lessThanThreeInContext1)
              .doOnError(t -> assertContext1());
    }

    subscribesInScope1RunsInScope2(source, errorSource).assertResult(1, 2);
  }

  @Test
  public void parallelFlowable_conditional_subscribesUnderScope() {
    ParallelFlowable<Integer> source =
        Flowable.range(1, 3)
            .parallel()
            .filter(lessThanThreeInContext2)
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2());
    ParallelFlowable<Integer> errorSource =
        Flowable.concat(
                Flowable.<Integer>error(new IllegalStateException()),
                Flowable.<Integer>error(new IllegalStateException()))
            .parallel()
            .filter(lessThanThreeInContext2)
            .doOnError(t -> assertContext2());

    subscribesAndRunsInScope2(source, errorSource).assertResult(1, 2);
  }

  @Test
  public void connectableFlowable() {
    ConnectableFlowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1())
              .publish();
      errorSource =
          Flowable.<Integer>error(new IllegalStateException())
              .doOnError(t -> assertContext1())
              .publish();
    }

    subscribesInScope1RunsInScope2(
            source.autoConnect().toObservable(), errorSource.autoConnect().toObservable())
        .assertResult(1, 2, 3);
  }

  @Test
  public void connectableFlowable_subscribesUnderScope() {
    ConnectableFlowable<Integer> source =
        Flowable.range(1, 3)
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2())
            .publish();
    ConnectableFlowable<Integer> errorSource =
        Flowable.<Integer>error(new IllegalStateException())
            .doOnError(t -> assertContext2())
            .publish();

    subscribesAndRunsInScope2(
            source.autoConnect().toObservable(), errorSource.autoConnect().toObservable())
        .assertResult(1, 2, 3);
  }

  @Test
  public void connectableFlowable_unwrappedWhenNotInScope() {
    assertThat(Flowable.range(1, 3).publish())
        .isInstanceOf(FlowablePublish.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void connectableFlowable_conditional() {
    ConnectableFlowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Flowable.range(1, 3)
              .filter(lessThanThreeInContext1)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1())
              .publish();
      errorSource =
          Flowable.<Integer>error(new IllegalStateException())
              .filter(lessThanThreeInContext1)
              .doOnError(t -> assertContext1())
              .publish();
    }

    subscribesInScope1RunsInScope2(
            source.autoConnect().toObservable(), errorSource.autoConnect().toObservable())
        .assertResult(1, 2);
  }

  @Test
  public void connectableFlowable_conditional_subscribesUnderScope() {
    ConnectableFlowable<Integer> source =
        Flowable.range(1, 3)
            .filter(lessThanThreeInContext2)
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2())
            .publish();
    ConnectableFlowable<Integer> errorSource =
        Flowable.<Integer>error(new IllegalStateException())
            .filter(lessThanThreeInContext2)
            .doOnError(t -> assertContext2())
            .publish();

    subscribesAndRunsInScope2(
            source.autoConnect().toObservable(), errorSource.autoConnect().toObservable())
        .assertResult(1, 2);
  }

  @Test
  public void connectableObservable() {
    ConnectableObservable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          Observable.range(1, 3)
              .doOnNext(i -> assertContext1())
              .doOnComplete(() -> assertContext1())
              .publish();
      errorSource =
          Observable.<Integer>error(new IllegalStateException())
              .doOnError(t -> assertContext1())
              .publish();
    }

    subscribesInScope1RunsInScope2(source.autoConnect(), errorSource.autoConnect())
        .assertResult(1, 2, 3);
  }

  @Test
  public void connectableObservable_subscribesUnderScope() {
    ConnectableObservable<Integer> source =
        Observable.range(1, 3)
            .doOnNext(i -> assertContext2())
            .doOnComplete(() -> assertContext2())
            .publish();
    ConnectableObservable<Integer> errorSource =
        Observable.<Integer>error(new IllegalStateException())
            .doOnError(t -> assertContext2())
            .publish();

    subscribesAndRunsInScope2(source.autoConnect(), errorSource.autoConnect())
        .assertResult(1, 2, 3);
  }

  @Test
  public void connectableObservable_unwrappedWhenNotInScope() {
    assertThat(Observable.range(1, 3).publish())
        .isInstanceOf(ObservablePublish.class); // is a final class so impossible we wrapped it
  }

  @Test
  public void callable_completable() throws Exception {
    Completable source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new CallableCompletable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new CallableCompletable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertComplete();
    callUnderScope2((Callable<Integer>) source, (Callable<Integer>) errorSource);
  }

  @Test
  public void callable_completable_subscribesUnderScope() throws Exception {
    Completable source =
        RxJavaPlugins.onAssembly(
            new CallableCompletable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Completable errorSource =
        RxJavaPlugins.onAssembly(
            new CallableCompletable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertComplete();

    ((Callable)
            RxJavaPlugins.onAssembly(
                new CallableCompletable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void callable_flowable() throws Exception {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new CallableFlowable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new CallableFlowable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void callable_flowable_subscribesUnderScope() throws Exception {
    Flowable<Integer> source =
        RxJavaPlugins.onAssembly(
            new CallableFlowable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Flowable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new CallableFlowable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new CallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void callable_flowable_conditional() throws Exception {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
                  new CallableFlowable<Integer>() {
                    @Override
                    public Integer call() {
                      assertContext1();
                      return 1;
                    }
                  })
              .filter(i -> i < 1);
      errorSource =
          RxJavaPlugins.onAssembly(
                  new CallableFlowable<Integer>() {
                    @Override
                    public Integer call() {
                      assertContext1();
                      throw new IllegalStateException();
                    }
                  })
              .filter(i -> i < 1);
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult();
  }

  @Test
  public void callable_flowable_conditional_subscribesUnderScope() throws Exception {
    Flowable<Integer> source =
        RxJavaPlugins.onAssembly(
                new CallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertContext2();
                    return 1;
                  }
                })
            .filter(i -> i < 1);
    Flowable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
                new CallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertContext2();
                    throw new IllegalStateException();
                  }
                })
            .filter(i -> i < 1);

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult();
  }

  @Test
  public void callable_maybe() throws Exception {
    Maybe<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new CallableMaybe<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new CallableMaybe<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void callable_maybe_subscribesUnderScope() throws Exception {
    Maybe<Integer> source =
        RxJavaPlugins.onAssembly(
            new CallableMaybe<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Maybe<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new CallableMaybe<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new CallableMaybe<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void callable_observable() throws Exception {
    Observable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new CallableObservable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new CallableObservable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source, errorSource).assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void callable_observable_subscribesUnderScope() throws Exception {
    Observable<Integer> source =
        RxJavaPlugins.onAssembly(
            new CallableObservable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Observable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new CallableObservable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source, errorSource).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new CallableObservable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void callable_single() throws Exception {
    Single<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new CallableSingle<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new CallableSingle<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void callable_single_subscribesUnderScope() throws Exception {
    Single<Integer> source =
        RxJavaPlugins.onAssembly(
            new CallableSingle<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Single<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new CallableSingle<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new CallableSingle<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void scalarcallable_completable() throws Exception {
    Completable source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new ScalarCallableCompletable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new ScalarCallableCompletable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult();
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void scalarcallable_completable_subscribesUnderScope() throws Exception {
    Completable source =
        RxJavaPlugins.onAssembly(
            new ScalarCallableCompletable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Completable errorSource =
        RxJavaPlugins.onAssembly(
            new ScalarCallableCompletable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult();

    ((Callable)
            RxJavaPlugins.onAssembly(
                new ScalarCallableCompletable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void scalarcallable_flowable() throws Exception {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new ScalarCallableFlowable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new ScalarCallableFlowable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void scalarcallable_flowable_subscribesUnderScope() throws Exception {
    Flowable<Integer> source =
        RxJavaPlugins.onAssembly(
            new ScalarCallableFlowable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Flowable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new ScalarCallableFlowable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new ScalarCallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void scalarcallable_flowable_conditional() throws Exception {
    Flowable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
                  new ScalarCallableFlowable<Integer>() {
                    @Override
                    public Integer call() {
                      assertContext1();
                      return 1;
                    }
                  })
              .filter(i -> i < 1);
      errorSource =
          RxJavaPlugins.onAssembly(
                  new ScalarCallableFlowable<Integer>() {
                    @Override
                    public Integer call() {
                      assertContext1();
                      throw new IllegalStateException();
                    }
                  })
              .filter(i -> i < 1);
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult();
  }

  @Test
  public void scalarcallable_flowable_conditional_subscribesUnderScope() throws Exception {
    Flowable<Integer> source =
        RxJavaPlugins.onAssembly(
                new ScalarCallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertContext2();
                    return 1;
                  }
                })
            .filter(i -> i < 1);
    Flowable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
                new ScalarCallableFlowable<Integer>() {
                  @Override
                  public Integer call() {
                    assertContext2();
                    throw new IllegalStateException();
                  }
                })
            .filter(i -> i < 1);

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult();
  }

  @Test
  public void scalarcallable_maybe() throws Exception {
    Maybe<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new ScalarCallableMaybe<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new ScalarCallableMaybe<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void scalarcallable_maybe_subscribesUnderScope() throws Exception {
    Maybe<Integer> source =
        RxJavaPlugins.onAssembly(
            new ScalarCallableMaybe<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Maybe<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new ScalarCallableMaybe<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new ScalarCallableMaybe<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void scalarcallable_observable() throws Exception {
    Observable<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new ScalarCallableObservable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new ScalarCallableObservable<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source, errorSource).assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void scalarcallable_observable_subscribesUnderScope() throws Exception {
    Observable<Integer> source =
        RxJavaPlugins.onAssembly(
            new ScalarCallableObservable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Observable<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new ScalarCallableObservable<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source, errorSource).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new ScalarCallableObservable<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void scalarcallable_single() throws Exception {
    Single<Integer> source, errorSource;
    try (Scope scope = currentTraceContext.newScope(context1)) {
      source =
          RxJavaPlugins.onAssembly(
              new ScalarCallableSingle<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  return 1;
                }
              });
      errorSource =
          RxJavaPlugins.onAssembly(
              new ScalarCallableSingle<Integer>() {
                @Override
                public Integer call() {
                  assertContext1();
                  throw new IllegalStateException();
                }
              });
    }

    subscribesInScope1RunsInScope2(source.toObservable(), errorSource.toObservable())
        .assertResult(1);
    callUnderScope2((Callable) source, (Callable) errorSource);
  }

  @Test
  public void scalarcallable_single_subscribesUnderScope() throws Exception {
    Single<Integer> source =
        RxJavaPlugins.onAssembly(
            new ScalarCallableSingle<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                return 1;
              }
            });
    Single<Integer> errorSource =
        RxJavaPlugins.onAssembly(
            new ScalarCallableSingle<Integer>() {
              @Override
              public Integer call() {
                assertContext2();
                throw new IllegalStateException();
              }
            });

    subscribesAndRunsInScope2(source.toObservable(), errorSource.toObservable()).assertResult(1);

    ((Callable)
            RxJavaPlugins.onAssembly(
                new ScalarCallableSingle<Integer>() {
                  @Override
                  public Integer call() {
                    assertNoContext();
                    return 1;
                  }
                }))
        .call();
  }

  @Test
  public void withAssemblyTracking() {
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

      SavedHooks h = contextTracking.enableAndChain();

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

  @Test
  public void withAssemblyTrackingOverride() {
    RxJavaAssemblyTracking.enable();
    try {
      contextTracking.enable();

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

  TestObserver<Integer> subscribesAndRunsInScope2(
      Observable<Integer> source, Observable<Integer> errorSource) {
    return runsInScope2(i -> assertContext2(), source, errorSource);
  }

  TestObserver<Integer> subscribesInScope1RunsInScope2(
      Observable<Integer> source, Observable<Integer> errorSource) {
    return runsInScope2(i -> assertContext1(), source, errorSource);
  }

  TestObserver<Integer> runsInScope2(
      Consumer<Void> onSubscribeContext,
      Observable<Integer> source,
      Observable<Integer> errorSource) {
    // Set another span between the time the source was created and subscribed.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      // callbacks after test subscribes should be in the second context
      errorSource
          .doOnSubscribe(s -> onSubscribeContext.accept(null))
          .doOnError(r -> assertContext2())
          .test()
          .assertFailure(IllegalStateException.class);

      return source
          .doOnSubscribe(s -> onSubscribeContext.accept(null))
          .doOnComplete(() -> assertContext2())
          .test();
    }
  }

  TestSubscriber<Integer> subscribesAndRunsInScope2(
      ParallelFlowable<Integer> source, ParallelFlowable<Integer> errorSource) {
    return runsInScope2(i -> assertContext2(), source, errorSource);
  }

  TestSubscriber<Integer> subscribesInScope1RunsInScope2(
      ParallelFlowable<Integer> source, ParallelFlowable<Integer> errorSource) {
    return runsInScope2(i -> assertContext1(), source, errorSource);
  }

  TestSubscriber<Integer> runsInScope2(
      Consumer<Void> onSubscribeContext,
      ParallelFlowable<Integer> source,
      ParallelFlowable<Integer> errorSource) {
    // Set another span between the time the source was created and subscribed.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      // callbacks after test subscribes should be in the second context
      errorSource
          .doOnSubscribe(s -> onSubscribeContext.accept(null))
          .doOnError(r -> assertContext2())
          .sequential()
          .test()
          .assertFailure(IllegalStateException.class);

      return source
          .doOnSubscribe(s -> onSubscribeContext.accept(null))
          .doOnComplete(() -> assertContext2())
          .sequential()
          .test();
    }
  }

  void assertNoContext() {
    assertThat(currentTraceContext.get()).isNull();
  }

  void assertContext1() {
    assertThat(currentTraceContext.get()).isEqualTo(context1);
  }

  void assertContext2() {
    assertThat(currentTraceContext.get()).isEqualTo(context2);
  }

  <T> void callUnderScope2(Callable<T> source, Callable<T> errorSource) throws Exception {
    // Set another span between the time the callable was created and invoked.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      try {
        errorSource.call();
        failBecauseExceptionWasNotThrown(IllegalStateException.class);
      } catch (IllegalStateException e) {
      }

      source.call();
    }
  }

  abstract class CallableCompletable<T> extends Completable implements Callable<T> {
    final CompletableFromCallable delegate = new CompletableFromCallable(this);

    @Override
    protected void subscribeActual(CompletableObserver s) {
      delegate.subscribe(s);
    }
  }

  abstract class CallableFlowable<T> extends Flowable<T> implements Callable<T> {
    final FlowableFromCallable<T> delegate = new FlowableFromCallable<>(this);

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class CallableMaybe<T> extends Maybe<T> implements Callable<T> {
    final MaybeFromCallable<T> delegate = new MaybeFromCallable<>(this);

    @Override
    protected void subscribeActual(MaybeObserver<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class CallableObservable<T> extends Observable<T> implements Callable<T> {
    final ObservableFromCallable<T> delegate = new ObservableFromCallable<>(this);

    @Override
    protected void subscribeActual(Observer<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class CallableSingle<T> extends Single<T> implements Callable<T> {
    final SingleFromCallable<T> delegate = new SingleFromCallable<>(this);

    @Override
    protected void subscribeActual(SingleObserver<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class ScalarCallableCompletable<T> extends Completable implements ScalarCallable<T> {
    final CompletableFromCallable delegate = new CompletableFromCallable(this);

    @Override
    protected void subscribeActual(CompletableObserver s) {
      delegate.subscribe(s);
    }
  }

  abstract class ScalarCallableFlowable<T> extends Flowable<T> implements ScalarCallable<T> {
    final FlowableFromCallable<T> delegate = new FlowableFromCallable<>(this);

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class ScalarCallableMaybe<T> extends Maybe<T> implements ScalarCallable<T> {
    final MaybeFromCallable<T> delegate = new MaybeFromCallable<>(this);

    @Override
    protected void subscribeActual(MaybeObserver<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class ScalarCallableObservable<T> extends Observable<T> implements ScalarCallable<T> {
    final ObservableFromCallable<T> delegate = new ObservableFromCallable<>(this);

    @Override
    protected void subscribeActual(Observer<? super T> s) {
      delegate.subscribe(s);
    }
  }

  abstract class ScalarCallableSingle<T> extends Single<T> implements ScalarCallable<T> {
    final SingleFromCallable<T> delegate = new SingleFromCallable<>(this);

    @Override
    protected void subscribeActual(SingleObserver<? super T> s) {
      delegate.subscribe(s);
    }
  }
}

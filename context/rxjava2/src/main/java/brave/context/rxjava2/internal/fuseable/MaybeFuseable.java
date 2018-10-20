package brave.context.rxjava2.internal.fuseable;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.fuseable.ScalarCallable;
import org.reactivestreams.Subscriber;

/**
 * Leniently tries to lookup the currently internal types {@link ScalarCallable} and {@link
 * ConditionalSubscriber}.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class MaybeFuseable {
  private static final MaybeFuseable INSTANCE = detectFuseable();

  public abstract <T> Subscriber<T> wrap(
      Subscriber<T> downstream,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext);

  public abstract Completable wrap(
      Completable actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext
  );

  public abstract <T> Maybe<T> wrap(
      Maybe<T> actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext
  );

  public abstract <T> Single<T> wrap(
      SingleSource<T> actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext
  );

  public abstract <T> Observable<T> wrap(
      Observable<T> actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext
  );

  public abstract <T> Flowable<T> wrap(
      Flowable<T> actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext
  );

  MaybeFuseable() {
  }

  public static MaybeFuseable get() {
    return INSTANCE;
  }

  /**
   * NOTE: If/when the fuseable package becomes non-internal, we'll likely need to do a reflective
   * approach to keep supporting the rxjava 2.x versions where these types were internal.
   */
  private static MaybeFuseable detectFuseable() {
    // Find fuseable classes. If they don't exist in this package or don't look right, we skip.
    try {
      // ScalarCallable only overrides Callable to remove exceptions. Assume this isn't reverted.
      Class.forName("io.reactivex.internal.fuseable.ScalarCallable");

      // ConditionalSubscriber has a single method. Assume the signature hasn't changed.
      Class<?> conditionalClass =
          Class.forName("io.reactivex.internal.fuseable.ConditionalSubscriber");
      if (conditionalClass.getMethod("tryOnNext", Object.class)
          .getReturnType().equals(boolean.class)) {
        return new Present();
      }
    } catch (Exception e) {
      // Maybe fuseable is no longer internal
    }

    return new Absent();
  }

  static final class Present extends MaybeFuseable {
    @Override public <T> Subscriber<T> wrap(Subscriber<T> downstream,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (downstream instanceof ConditionalSubscriber) {
        return new TraceContextConditionalSubscriber<>((ConditionalSubscriber<T>) downstream,
            currentTraceContext, assemblyContext);
      }
      return new TraceContextSubscriber<>(downstream, currentTraceContext, assemblyContext);
    }

    @Override public Completable wrap(Completable actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (actual instanceof ScalarCallable) {
        return new TraceContextScalarCallableCompletable<>(actual, currentTraceContext,
            assemblyContext);
      }
      return new TraceContextCallableCompletable<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Maybe<T> wrap(Maybe<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (actual instanceof ScalarCallable) {
        return new TraceContextScalarCallableMaybe<>(actual, currentTraceContext, assemblyContext);
      }
      return new TraceContextCallableMaybe<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Single<T> wrap(SingleSource<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (actual instanceof ScalarCallable) {
        return new TraceContextScalarCallableSingle<>(actual, currentTraceContext, assemblyContext);
      }
      return new TraceContextCallableSingle<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Observable<T> wrap(Observable<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (actual instanceof ScalarCallable) {
        return new TraceContextScalarCallableObservable<>(actual, currentTraceContext,
            assemblyContext);
      }
      return new TraceContextCallableObservable<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Flowable<T> wrap(Flowable<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      if (actual instanceof ScalarCallable) {
        return new TraceContextScalarCallableFlowable<>(actual, currentTraceContext,
            assemblyContext);
      }
      return new TraceContextCallableFlowable<>(actual, currentTraceContext, assemblyContext);
    }
  }

  static final class Absent extends MaybeFuseable {
    @Override public <T> Subscriber<T> wrap(Subscriber<T> downstream,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextSubscriber<>(downstream, currentTraceContext, assemblyContext);
    }

    @Override public Completable wrap(Completable actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextCallableCompletable<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Maybe<T> wrap(Maybe<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextCallableMaybe<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Single<T> wrap(SingleSource<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextCallableSingle<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Observable<T> wrap(Observable<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextCallableObservable<>(actual, currentTraceContext, assemblyContext);
    }

    @Override public <T> Flowable<T> wrap(Flowable<T> actual,
        CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
      return new TraceContextCallableFlowable<>(actual, currentTraceContext, assemblyContext);
    }
  }
}

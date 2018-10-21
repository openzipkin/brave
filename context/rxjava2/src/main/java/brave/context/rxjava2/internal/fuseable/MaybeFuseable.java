package brave.context.rxjava2.internal.fuseable;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.fuseable.ScalarCallable;
import org.reactivestreams.Publisher;
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
      Subscriber<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract Completable wrap(
      CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> Maybe<T> wrap(
      MaybeSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> Single<T> wrap(
      SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> Observable<T> wrap(
      ObservableSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled);

  public abstract <T> Flowable<T> wrap(
      Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled);

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
    @Override public <T> Subscriber<T> wrap(
        Subscriber<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (downstream instanceof ConditionalSubscriber) {
        return new TraceContextConditionalSubscriber<>((ConditionalSubscriber<T>) downstream,
            contextScoper, assembled);
      }
      return new TraceContextSubscriber<>(downstream, contextScoper, assembled);
    }

    @Override public Completable wrap(
        CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (source instanceof ScalarCallable) {
        return new TraceContextScalarCallableCompletable<>(source, contextScoper, assembled);
      }
      return new TraceContextCallableCompletable<>(source, contextScoper, assembled);
    }

    @Override public <T> Maybe<T> wrap(
        MaybeSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (source instanceof ScalarCallable) {
        return new TraceContextScalarCallableMaybe<>(source, contextScoper, assembled);
      }
      return new TraceContextCallableMaybe<>(source, contextScoper, assembled);
    }

    @Override public <T> Single<T> wrap(
        SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (source instanceof ScalarCallable) {
        return new TraceContextScalarCallableSingle<>(source, contextScoper, assembled);
      }
      return new TraceContextCallableSingle<>(source, contextScoper, assembled);
    }

    @Override public <T> Observable<T> wrap(
        ObservableSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (source instanceof ScalarCallable) {
        return new TraceContextScalarCallableObservable<>(source, contextScoper, assembled);
      }
      return new TraceContextCallableObservable<>(source, contextScoper, assembled);
    }

    @Override public <T> Flowable<T> wrap(
        Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      if (source instanceof ScalarCallable) {
        return new TraceContextScalarCallableFlowable<>(source, contextScoper, assembled);
      }
      return new TraceContextCallableFlowable<>(source, contextScoper, assembled);
    }
  }

  static final class Absent extends MaybeFuseable {
    @Override public <T> Subscriber<T> wrap
        (Subscriber<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextSubscriber<>(downstream, contextScoper, assembled);
    }

    @Override public Completable wrap(
        CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextCallableCompletable<>(source, contextScoper, assembled);
    }

    @Override public <T> Maybe<T> wrap(
        MaybeSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextCallableMaybe<>(source, contextScoper, assembled);
    }

    @Override public <T> Single<T> wrap(
        SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextCallableSingle<>(source, contextScoper, assembled);
    }

    @Override public <T> Observable<T> wrap(
        ObservableSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextCallableObservable<>(source, contextScoper, assembled);
    }

    @Override public <T> Flowable<T> wrap(
        Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
      return new TraceContextCallableFlowable<>(source, contextScoper, assembled);
    }
  }
}

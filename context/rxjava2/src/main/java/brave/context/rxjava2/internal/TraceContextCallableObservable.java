package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import java.util.concurrent.Callable;

final class TraceContextCallableObservable<T> extends Observable<T> implements Callable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextCallableObservable(
      ObservableSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the observer so that its callbacks run in the assembly context. This does not affect any
   * subscription callbacks.
   */
  @Override protected void subscribeActual(Observer<? super T> o) {
    source.subscribe(Wrappers.wrap(o, contextScoper, assembled));
  }

  /**
   * The value in the source is computed synchronously, at subscription time. We don't re-scope this
   * call because it would interfere with the subscription context.
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() throws Exception {
    return ((Callable<T>) source).call();
  }
}

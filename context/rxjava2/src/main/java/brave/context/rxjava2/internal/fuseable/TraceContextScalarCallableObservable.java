package brave.context.rxjava2.internal.fuseable;

import brave.context.rxjava2.Internal;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.functions.Function;
import io.reactivex.internal.fuseable.ScalarCallable;

final class TraceContextScalarCallableObservable<T> extends Observable<T>
    implements ScalarCallable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextScalarCallableObservable(
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
    source.subscribe(Internal.instance.wrap(o, contextScoper, assembled));
  }

  /**
   * The value retained in the source is computed at assembly time. It is intended to be evaluated
   * during assembly functions such as {@link Observable#switchMap(Function)}. We don't scope around
   * this call because it is reading a constant.
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() {
    return ((ScalarCallable<T>) source).call();
  }
}

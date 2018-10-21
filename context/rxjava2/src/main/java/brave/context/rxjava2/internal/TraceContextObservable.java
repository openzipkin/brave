package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;

final class TraceContextObservable<T> extends Observable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextObservable(
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
    source.subscribe(new TraceContextObserver<>(o, contextScoper, assembled));
  }
}

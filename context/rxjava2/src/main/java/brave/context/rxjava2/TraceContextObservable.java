package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;

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

  @Override protected void subscribeActual(io.reactivex.Observer<? super T> s) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new TraceContextObserver<>(s, contextScoper, assembled));
    } finally {
      scope.close();
    }
  }
}

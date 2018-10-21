package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observables.ConnectableObservable;

final class TraceContextConnectableObservable<T> extends ConnectableObservable<T> {
  final ConnectableObservable<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextConnectableObservable(
      ConnectableObservable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
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

  @Override public void connect(Consumer<? super Disposable> connection) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.connect(connection);
    } finally {
      scope.close();
    }
  }
}

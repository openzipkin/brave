package brave.context.rxjava2;

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

  @Override protected void subscribeActual(Observer s) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new TraceContextObserver<T>(s, contextScoper, assembled));
    } finally {
      scope.close();
    }
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

package brave.context.rxjava2;

import brave.context.rxjava2.internal.Util;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

final class TraceContextSingleObserver<T> implements SingleObserver<T>, Disposable {
  final SingleObserver<T> downstream;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;
  Disposable upstream;

  TraceContextSingleObserver(
      SingleObserver<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.downstream = downstream;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override public void onSubscribe(Disposable d) {
    if (!Util.validate(upstream, d)) return;
    upstream = d;
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onSubscribe(this);
    } finally {
      scope.close();
    }
  }

  @Override public void onError(Throwable t) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onSuccess(T value) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onSuccess(value);
    } finally {
      scope.close();
    }
  }

  @Override public boolean isDisposed() {
    return upstream.isDisposed();
  }

  @Override public void dispose() {
    upstream.dispose();
  }
}

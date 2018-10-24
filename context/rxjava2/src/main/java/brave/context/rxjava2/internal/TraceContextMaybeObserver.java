package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

final class TraceContextMaybeObserver<T> implements MaybeObserver<T>, Disposable {
  final MaybeObserver<T> downstream;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;
  Disposable upstream;

  TraceContextMaybeObserver(
      MaybeObserver<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.downstream = downstream;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override public void onSubscribe(Disposable d) {
    if (!Util.validate(upstream, d)) return;
    upstream = d;

    // Operators need to detect the fuseable feature of their immediate upstream. We pass "this"
    // to ensure downstream don't interface with the wrong operator (s).
    downstream.onSubscribe(this);
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

  @Override public void onComplete() {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onComplete();
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

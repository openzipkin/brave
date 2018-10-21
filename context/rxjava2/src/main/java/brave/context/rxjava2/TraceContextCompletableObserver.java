package brave.context.rxjava2;

import brave.context.rxjava2.internal.Util;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.Disposable;

final class TraceContextCompletableObserver implements CompletableObserver, Disposable {
  final CompletableObserver actual;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;
  Disposable d;

  TraceContextCompletableObserver(
      CompletableObserver actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.actual = actual;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override public void onSubscribe(Disposable d) {
    if (!Util.validate(this.d, d)) return;
    this.d = d;
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      actual.onSubscribe(this);
    } finally {
      scope.close();
    }
  }

  @Override public void onError(Throwable t) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      actual.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onComplete() {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      actual.onComplete();
    } finally {
      scope.close();
    }
  }

  @Override public boolean isDisposed() {
    return d.isDisposed();
  }

  @Override public void dispose() {
    d.dispose();
  }
}

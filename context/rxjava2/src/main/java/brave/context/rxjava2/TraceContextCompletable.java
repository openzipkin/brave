package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;

final class TraceContextCompletable extends Completable {
  final CompletableSource source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextCompletable(
      CompletableSource source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(CompletableObserver s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new Observer(s, currentTraceContext, assemblyContext));
    } finally {
      scope.close();
    }
  }

  static final class Observer implements CompletableObserver, Disposable {
    final CompletableObserver actual;
    final CurrentTraceContext currentTraceContext;
    final TraceContext assemblyContext;
    Disposable d;

    Observer(
        CompletableObserver actual,
        CurrentTraceContext currentTraceContext,
        TraceContext assemblyContext) {
      this.actual = actual;
      this.currentTraceContext = currentTraceContext;
      this.assemblyContext = assemblyContext;
    }

    @Override
    public void onSubscribe(Disposable d) {
      if (!DisposableHelper.validate(this.d, d)) return;
      this.d = d;
      Scope scope = currentTraceContext.maybeScope(assemblyContext);
      try { // retrolambda can't resolve this try/finally
        actual.onSubscribe(this);
      } finally {
        scope.close();
      }
    }

    @Override
    public void onError(Throwable t) {
      Scope scope = currentTraceContext.maybeScope(assemblyContext);
      try { // retrolambda can't resolve this try/finally
        actual.onError(t);
      } finally {
        scope.close();
      }
    }

    @Override
    public void onComplete() {
      Scope scope = currentTraceContext.maybeScope(assemblyContext);
      try { // retrolambda can't resolve this try/finally
        actual.onComplete();
      } finally {
        scope.close();
      }
    }

    @Override
    public boolean isDisposed() {
      return d.isDisposed();
    }

    @Override
    public void dispose() {
      d.dispose();
    }
  }
}

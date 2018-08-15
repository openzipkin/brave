package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;

final class TraceContextSingle<T> extends Single<T> {
  final SingleSource<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextSingle(
      SingleSource<T> source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(SingleObserver<? super T> s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new Observer<>(s, currentTraceContext, assemblyContext));
    } finally {
      scope.close();
    }
  }

  static final class Observer<T> implements SingleObserver<T>, Disposable {
    final SingleObserver<T> actual;
    final CurrentTraceContext currentTraceContext;
    final TraceContext assemblyContext;
    Disposable d;

    Observer(
        SingleObserver actual,
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
    public void onSuccess(T value) {
      Scope scope = currentTraceContext.maybeScope(assemblyContext);
      try { // retrolambda can't resolve this try/finally
        actual.onSuccess(value);
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

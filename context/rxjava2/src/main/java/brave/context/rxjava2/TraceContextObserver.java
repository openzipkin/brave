package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.plugins.RxJavaPlugins;

final class TraceContextObserver<T> implements Observer<T> {
  final Observer<T> downstream;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;
  Disposable upstream;
  boolean done;

  TraceContextObserver(
      Observer<T> downstream,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.downstream = downstream;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override public final void onSubscribe(Disposable d) {
    if (DisposableHelper.validate(upstream, d)) {
      downstream.onSubscribe((upstream = d));
    }
  }

  @Override public void onNext(T t) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onNext(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onError(Throwable t) {
    if (done) {
      RxJavaPlugins.onError(t);
      return;
    }
    done = true;

    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onComplete() {
    if (done) return;
    done = true;

    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onComplete();
    } finally {
      scope.close();
    }
  }
}

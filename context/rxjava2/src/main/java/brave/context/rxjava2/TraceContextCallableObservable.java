package brave.context.rxjava2;

import brave.context.rxjava2.TraceContextObservable.Observer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import java.util.concurrent.Callable;

final class TraceContextCallableObservable<T> extends Observable<T> implements Callable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextCallableObservable(
      ObservableSource<T> source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(io.reactivex.Observer<? super T> s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new Observer<>(s, currentTraceContext, assemblyContext));
    } finally {
      scope.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T call() throws Exception {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      return ((Callable<T>) source).call();
    } finally {
      scope.close();
    }
  }
}

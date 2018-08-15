package brave.context.rxjava2;

import brave.context.rxjava2.TraceContextSingle.Observer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.internal.fuseable.ScalarCallable;

final class TraceContextScalarCallableSingle<T> extends Single<T> implements ScalarCallable<T> {
  final SingleSource<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextScalarCallableSingle(
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

  @SuppressWarnings("unchecked")
  @Override
  public T call() {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      return ((ScalarCallable<T>) source).call();
    } finally {
      scope.close();
    }
  }
}

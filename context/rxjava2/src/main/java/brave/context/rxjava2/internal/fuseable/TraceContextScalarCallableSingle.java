package brave.context.rxjava2.internal.fuseable;

import brave.context.rxjava2.Internal;
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

  @Override protected void subscribeActual(SingleObserver<? super T> s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(Internal.instance.wrap(s, currentTraceContext, assemblyContext));
    } finally {
      scope.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Override public T call() {
    // Instrumentation overhead does not make sense when returning a scalar (constant) value.
    return ((ScalarCallable<T>) source).call();
  }
}

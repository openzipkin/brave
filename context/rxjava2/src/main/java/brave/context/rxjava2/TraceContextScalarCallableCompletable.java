package brave.context.rxjava2;

import brave.context.rxjava2.TraceContextCompletable.Observer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import io.reactivex.internal.fuseable.ScalarCallable;

final class TraceContextScalarCallableCompletable<T> extends Completable
    implements ScalarCallable<T> {
  final CompletableSource source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextScalarCallableCompletable(
      CompletableSource source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(CompletableObserver s) {
    try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      source.subscribe(new Observer(s, currentTraceContext, assemblyContext));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T call() {
    try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      return ((ScalarCallable<T>) source).call();
    }
  }
}

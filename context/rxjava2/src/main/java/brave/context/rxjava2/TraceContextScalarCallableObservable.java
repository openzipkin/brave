package brave.context.rxjava2;

import brave.context.rxjava2.TraceContextObservable.Observer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.internal.fuseable.ScalarCallable;

final class TraceContextScalarCallableObservable<T> extends Observable<T>
    implements ScalarCallable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextScalarCallableObservable(
      ObservableSource<T> source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(io.reactivex.Observer<? super T> s) {
    try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      source.subscribe(new Observer<>(s, currentTraceContext, assemblyContext));
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

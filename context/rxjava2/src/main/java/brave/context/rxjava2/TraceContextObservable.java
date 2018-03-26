package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.internal.fuseable.QueueDisposable;
import io.reactivex.internal.observers.BasicFuseableObserver;

final class TraceContextObservable<T> extends Observable<T> {
  final ObservableSource<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextObservable(
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

  static final class Observer<T> extends BasicFuseableObserver<T, T> {
    final CurrentTraceContext currentTraceContext;
    final TraceContext assemblyContext;

    Observer(
        io.reactivex.Observer<T> actual,
        CurrentTraceContext currentTraceContext,
        TraceContext assemblyContext) {
      super(actual);
      this.currentTraceContext = currentTraceContext;
      this.assemblyContext = assemblyContext;
    }

    @Override
    public void onNext(T t) {
      try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
        actual.onNext(t);
      }
    }

    @Override
    public void onError(Throwable t) {
      try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
        actual.onError(t);
      }
    }

    @Override
    public void onComplete() {
      try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
        actual.onComplete();
      }
    }

    @Override
    public int requestFusion(int mode) {
      QueueDisposable<T> qs = this.qs;
      if (qs != null) {
        int m = qs.requestFusion(mode);
        sourceMode = m;
        return m;
      }
      return NONE;
    }

    @Override
    public T poll() throws Exception {
      return qs.poll();
    }
  }
}

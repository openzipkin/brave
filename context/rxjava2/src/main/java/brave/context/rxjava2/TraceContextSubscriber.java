package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscribers.BasicFuseableSubscriber;

final class TraceContextSubscriber<T> extends BasicFuseableSubscriber<T, T> {
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextSubscriber(
      org.reactivestreams.Subscriber actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    super(actual);
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  public void onNext(T t) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      actual.onNext(t);
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
  public int requestFusion(int mode) {
    QueueSubscription<T> qs = this.qs;
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

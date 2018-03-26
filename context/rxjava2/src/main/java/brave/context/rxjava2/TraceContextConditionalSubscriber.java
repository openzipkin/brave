package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscribers.BasicFuseableConditionalSubscriber;

final class TraceContextConditionalSubscriber<T> extends BasicFuseableConditionalSubscriber<T, T> {
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextConditionalSubscriber(
      io.reactivex.internal.fuseable.ConditionalSubscriber actual,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    super(actual);
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  public boolean tryOnNext(T t) {
    try (Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      return actual.tryOnNext(t);
    }
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

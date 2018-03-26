package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import java.util.concurrent.Callable;
import org.reactivestreams.Publisher;

final class TraceContextCallableFlowable<T> extends Flowable<T> implements Callable<T> {
  final Publisher<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextCallableFlowable(
      Publisher<T> source, CurrentTraceContext currentTraceContext, TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(org.reactivestreams.Subscriber<? super T> s) {
    try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      if (s instanceof ConditionalSubscriber) {
        source.subscribe(
            new TraceContextConditionalSubscriber<>(
                (ConditionalSubscriber) s, currentTraceContext, assemblyContext));
      } else {
        source.subscribe(new TraceContextSubscriber<>(s, currentTraceContext, assemblyContext));
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T call() throws Exception {
    try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(assemblyContext)) {
      return ((Callable<T>) source).call();
    }
  }
}

package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Subscriber;

final class TraceContextParallelFlowable<T> extends ParallelFlowable<T> {
  final ParallelFlowable<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextParallelFlowable(
      ParallelFlowable<T> source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  public int parallelism() {
    return source.parallelism();
  }

  @Override
  public void subscribe(Subscriber<? super T>[] s) {
    if (!validate(s)) return;
    int n = s.length;
    @SuppressWarnings("unchecked")
    Subscriber<? super T>[] parents = new Subscriber[n];
    for (int i = 0; i < n; i++) {
      Subscriber<? super T> z = s[i];
      if (z instanceof ConditionalSubscriber) {
        parents[i] =
            new TraceContextConditionalSubscriber<>(
                (ConditionalSubscriber<? super T>) z, currentTraceContext, assemblyContext);
      } else {
        parents[i] = new TraceContextSubscriber<>(z, currentTraceContext, assemblyContext);
      }
    }
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(parents);
    } finally {
      scope.close();
    }
  }
}

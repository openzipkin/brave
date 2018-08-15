package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.ConditionalSubscriber;

final class TraceContextConnectableFlowable<T> extends ConnectableFlowable<T> {
  final ConnectableFlowable<T> source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextConnectableFlowable(
      ConnectableFlowable<T> source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override
  protected void subscribeActual(org.reactivestreams.Subscriber<? super T> s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      if (s instanceof ConditionalSubscriber) {
        source.subscribe(
            new TraceContextConditionalSubscriber<>(
                (ConditionalSubscriber) s, currentTraceContext, assemblyContext));
      } else {
        source.subscribe(new TraceContextSubscriber<>(s, currentTraceContext, assemblyContext));
      }
    } finally {
      scope.close();
    }
  }

  @Override
  public void connect(Consumer<? super Disposable> connection) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.connect(connection);
    } finally {
      scope.close();
    }
  }
}

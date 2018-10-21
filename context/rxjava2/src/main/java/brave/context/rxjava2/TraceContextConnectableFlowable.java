package brave.context.rxjava2;

import brave.context.rxjava2.internal.fuseable.MaybeFuseable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;

final class TraceContextConnectableFlowable<T> extends ConnectableFlowable<T> {
  final ConnectableFlowable<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextConnectableFlowable(
      ConnectableFlowable<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override protected void subscribeActual(org.reactivestreams.Subscriber<? super T> s) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(MaybeFuseable.get().wrap(s, contextScoper, assembled));
    } finally {
      scope.close();
    }
  }

  @Override public void connect(Consumer<? super Disposable> connection) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.connect(connection);
    } finally {
      scope.close();
    }
  }
}

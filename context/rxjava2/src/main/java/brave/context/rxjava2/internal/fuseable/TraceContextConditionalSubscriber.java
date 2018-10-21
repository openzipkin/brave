package brave.context.rxjava2.internal.fuseable;

import brave.context.rxjava2.internal.Util;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.plugins.RxJavaPlugins;
import org.reactivestreams.Subscription;

final class TraceContextConditionalSubscriber<T> implements ConditionalSubscriber<T> {
  final ConditionalSubscriber<T> downstream;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;
  Subscription upstream;
  boolean done;

  TraceContextConditionalSubscriber(
      ConditionalSubscriber<T> downstream,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.downstream = downstream;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override public final void onSubscribe(Subscription s) {
    if (Util.validate(upstream, s)) {
      downstream.onSubscribe((upstream = s));
    }
  }

  @Override public boolean tryOnNext(T t) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      return downstream.tryOnNext(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onNext(T t) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onNext(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onError(Throwable t) {
    if (done) {
      RxJavaPlugins.onError(t);
      return;
    }
    done = true;

    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onComplete() {
    if (done) return;
    done = true;

    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      downstream.onComplete();
    } finally {
      scope.close();
    }
  }
}

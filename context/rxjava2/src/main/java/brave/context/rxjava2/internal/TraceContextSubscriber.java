package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.plugins.RxJavaPlugins;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class TraceContextSubscriber<T> implements Subscriber<T> {
  final Subscriber<T> downstream;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  Subscription upstream;
  boolean done;

  TraceContextSubscriber(
      Subscriber<T> downstream, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.downstream = downstream;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override public final void onSubscribe(Subscription s) {
    if (!Util.validate(upstream, s)) return;
    upstream = s;

    // Operators need to detect the fuseable feature of their immediate upstream. We pass "this"
    // to ensure downstream don't interface with the wrong operator (s).
    downstream.onSubscribe(upstream);
  }

  @Override public void onNext(T t) {
    Scope scope = contextScoper.maybeScope(assembled);
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

    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onError(t);
    } finally {
      scope.close();
    }
  }

  @Override public void onComplete() {
    if (done) return;
    done = true;

    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      downstream.onComplete();
    } finally {
      scope.close();
    }
  }
}

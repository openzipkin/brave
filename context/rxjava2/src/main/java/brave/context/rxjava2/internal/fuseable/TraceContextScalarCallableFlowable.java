package brave.context.rxjava2.internal.fuseable;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.internal.fuseable.ScalarCallable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public final class TraceContextScalarCallableFlowable<T> extends Flowable<T>
    implements ScalarCallable<T> {
  final Publisher<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextScalarCallableFlowable(
      Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override protected void subscribeActual(Subscriber<? super T> s) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(MaybeFuseable.get().wrap(s, contextScoper, assembled));
    } finally {
      scope.close();
    }
  }

  /**
   * A scalar value is computed at assembled time. Since call() is at runtime, we shouldn't add
   * overhead of scoping, only to return a constant!
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() {
    return ((ScalarCallable<T>) source).call();
  }
}

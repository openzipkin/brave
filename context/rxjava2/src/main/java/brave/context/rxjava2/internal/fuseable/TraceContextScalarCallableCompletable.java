package brave.context.rxjava2.internal.fuseable;

import brave.context.rxjava2.Internal;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import io.reactivex.internal.fuseable.ScalarCallable;

public final class TraceContextScalarCallableCompletable<T> extends Completable
    implements ScalarCallable<T> {
  final CompletableSource source;
  final CurrentTraceContext currentTraceContext;
  final TraceContext assemblyContext;

  TraceContextScalarCallableCompletable(
      CompletableSource source,
      CurrentTraceContext currentTraceContext,
      TraceContext assemblyContext) {
    this.source = source;
    this.currentTraceContext = currentTraceContext;
    this.assemblyContext = assemblyContext;
  }

  @Override protected void subscribeActual(CompletableObserver s) {
    Scope scope = currentTraceContext.maybeScope(assemblyContext);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(Internal.instance.wrap(s, currentTraceContext, assemblyContext));
    } finally {
      scope.close();
    }
  }

  /**
   * A scalar value is computed at assembly time. Since call() is at runtime, we shouldn't add
   * overhead of scoping, only to return a constant!
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() {
    return ((ScalarCallable<T>) source).call();
  }
}

package brave.context.rxjava2;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;

final class TraceContextCompletable extends Completable {
  final CompletableSource source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextCompletable(
      CompletableSource source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  @Override
  protected void subscribeActual(CompletableObserver s) {
    Scope scope = contextScoper.maybeScope(assembled);
    try { // retrolambda can't resolve this try/finally
      source.subscribe(new TraceContextCompletableObserver(s, contextScoper, assembled));
    } finally {
      scope.close();
    }
  }
}

package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;

final class TraceContextSingle<T> extends Single<T> {
  final SingleSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextSingle(
      SingleSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the observer so that its callbacks run in the assembly context. This does not affect any
   * subscription callbacks.
   */
  @Override protected void subscribeActual(SingleObserver<? super T> o) {
    source.subscribe(new TraceContextSingleObserver<>(o, contextScoper, assembled));
  }
}

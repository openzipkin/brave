package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;

final class TraceContextMaybe<T> extends Maybe<T> {
  final MaybeSource<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextMaybe(
      MaybeSource<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the observer so that its callbacks run in the assembly context. This does not affect any
   * subscription callbacks.
   */
  @Override protected void subscribeActual(MaybeObserver<? super T> o) {
    source.subscribe(new TraceContextMaybeObserver<>(o, contextScoper, assembled));
  }
}

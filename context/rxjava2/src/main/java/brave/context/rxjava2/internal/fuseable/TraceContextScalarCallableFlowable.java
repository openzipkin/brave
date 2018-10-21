package brave.context.rxjava2.internal.fuseable;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
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

  /**
   * Wraps the subscriber so that its callbacks run in the assembly context. This does not affect
   * any subscription callbacks.
   */
  @Override protected void subscribeActual(Subscriber<? super T> s) {
    source.subscribe(MaybeFuseable.get().wrap(s, contextScoper, assembled));
  }

  /**
   * The value retained in the source is computed at assembly time. It is intended to be evaluated
   * during assembly functions such as {@link Flowable#switchMap(Function)}. We don't scope around
   * this call because it is reading a constant.
   *
   * <p>See https://github.com/ReactiveX/RxJava/wiki/Writing-operators-for-2.0#callable-and-scalarcallable
   */
  @Override @SuppressWarnings("unchecked") public T call() {
    return ((ScalarCallable<T>) source).call();
  }
}

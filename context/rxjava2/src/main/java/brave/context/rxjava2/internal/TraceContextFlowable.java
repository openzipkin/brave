package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

final class TraceContextFlowable<T> extends Flowable<T> {
  final Publisher<T> source;
  final CurrentTraceContext contextScoper;
  final TraceContext assembled;

  TraceContextFlowable(
      Publisher<T> source, CurrentTraceContext contextScoper, TraceContext assembled) {
    this.source = source;
    this.contextScoper = contextScoper;
    this.assembled = assembled;
  }

  /**
   * Wraps the subscriber so that its callbacks run in the assembly context. This does not affect
   * any subscription callbacks.
   *
   * <p>Note: per {@link #subscribe(Subscriber)} only calls this with a {@link FlowableSubscriber}
   */
  @Override protected void subscribeActual(Subscriber<? super T> s) {
    assert s instanceof FlowableSubscriber : "!(s instanceof FlowableSubscriber)";
    source.subscribe(Wrappers.wrap(s, contextScoper, assembled));
  }
}

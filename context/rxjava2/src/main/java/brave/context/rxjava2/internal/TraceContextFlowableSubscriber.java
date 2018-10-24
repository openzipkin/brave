package brave.context.rxjava2.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * We implement {@linkplain FlowableSubscriber}, not {@linkplain Subscriber} as the only call site
 * is {@link Flowable#subscribeActual(Subscriber)} which is guaranteed to only take a {@linkplain
 * FlowableSubscriber}.
 */
class TraceContextFlowableSubscriber<T> extends TraceContextSubscriber<T>
    implements FlowableSubscriber<T>, Subscription {

  TraceContextFlowableSubscriber(
      FlowableSubscriber<T> downstream, CurrentTraceContext contextScoper,
      TraceContext assembled) {
    super(downstream, contextScoper, assembled);
  }

  @Override public void request(long n) {
    upstream.request(n);
  }

  @Override public void cancel() {
    upstream.cancel();
  }
}

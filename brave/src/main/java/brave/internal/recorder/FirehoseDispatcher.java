package brave.internal.recorder;

import brave.firehose.FirehoseHandler;
import brave.internal.firehose.FirehoseHandlers;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches {@link TraceContext#sampled() sampled} spans to Zipkin after forwarding to any default
 * firehoseHandler.
 */
public final class FirehoseDispatcher {

  final FirehoseHandler firehoseHandler;
  final AtomicBoolean noop = new AtomicBoolean();
  final boolean alwaysSampleLocal;

  public FirehoseDispatcher(List<FirehoseHandler.Factory> firehoseHandlerFactories,
      String serviceName, String ip, int port) {
    boolean alwaysSampleLocal = false;
    for (FirehoseHandler.Factory factory : firehoseHandlerFactories) {
      if (factory.alwaysSampleLocal()) alwaysSampleLocal = true;
    }
    this.alwaysSampleLocal = alwaysSampleLocal;
    this.firehoseHandler = FirehoseHandlers.noopAware(FirehoseHandlers.compose(
        FirehoseHandlers.create(firehoseHandlerFactories, serviceName, ip, port)), noop);
  }

  /**
   * Returns a firehoseHandler that accepts data according to configuration or {@link
   * FirehoseHandler#NOOP} if there is nothing to dispatch to.
   */
  public FirehoseHandler firehoseHandler() {
    return firehoseHandler;
  }

  /** @see FirehoseHandler.Factory#alwaysSampleLocal() */
  public boolean alwaysSampleLocal() {
    return alwaysSampleLocal;
  }

  public AtomicBoolean noop() {
    return noop;
  }

  @Override public String toString() {
    return firehoseHandler.toString();
  }
}

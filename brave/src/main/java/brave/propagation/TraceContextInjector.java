package brave.propagation;

import brave.TraceContext;

/**
 * @param <C> carrier of propagated trace data. For example, a http request or message.
 */
public interface TraceContextInjector<C> {
  /**
   * Calls the setter for each key to send downstream.
   *
   * @param traceContext possibly unsampled.
   * @param carrier holds the propagated keys. For example, an outgoing message or http request.
   */
  void inject(TraceContext traceContext, C carrier);
}

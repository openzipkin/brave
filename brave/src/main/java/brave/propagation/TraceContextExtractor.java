package brave.propagation;

import brave.TraceContext;

/**
 * @param <C> carrier of propagated trace data. For example, a http request or message.
 */
public interface TraceContextExtractor<C> {
  /**
   * Calls the getter for each key needed to create trace data. Creates a new Trace if ids are
   * unreadable or not present.
   *
   * @param carrier holds the propagated keys. For example, an incoming message or http request.
   */
  TraceContext extract(C carrier);
}

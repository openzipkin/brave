package brave.internal.recorder;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;

public interface Firehose {

  abstract class Factory {
    /**
     * Creates a firehose given the local endpoint of {@link Tracing}.
     *
     * <p>This is used to cache data to reduce object allocations.
     */
    public abstract Firehose create(String serviceName, String ip, int port);

    /**
     * When true, all spans become real spans even if they aren't sampled remotely. This allows
     * firehose instances (such as metrics) to consider attributes that are not always visible
     * before-the-fact, such as http paths. Defaults to false and affects {@link
     * TraceContext#sampledLocal()}.
     *
     * @see Firehose#accept(TraceContext, MutableSpan)
     */
    public boolean alwaysSampleLocal() {
      return false;
    }
  }

  /**
   * Triggered on each finished span except when spans that are {@link Span#isNoop() no-op}.
   *
   * <p>{@link TraceContext#sampled() Sampled spans} hit this stage before reporting to Zipkin.
   * This means changes to the mutable span will reflect in reported data.
   *
   * <p>When Zipkin's reporter is {@link zipkin2.reporter.Reporter#NOOP} or the context is
   * unsampled, this will still receive spans where {@link TraceContext#sampledLocal()} is true.
   *
   * @see Factory#alwaysSampleLocal()
   */
  void accept(TraceContext context, MutableSpan span);
}

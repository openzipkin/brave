package brave.firehose;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;

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
public interface FirehoseHandler {
  /** Use to avoid comparing against null references */
  FirehoseHandler NOOP = new FirehoseHandler() {
    @Override public void accept(TraceContext context, MutableSpan span) {
    }

    @Override public String toString() {
      return "NoopFirehoseHandler{}";
    }
  };

  abstract class Factory {
    /**
     * Creates a firehose given the local endpoint of {@link Tracing}.
     *
     * <p>When {@link FirehoseHandler#accept(TraceContext, MutableSpan) accepting} into streams such
     * as Zipkin, these values should be used when not specified in the {@link MutableSpan}.
     *
     * @param serviceName default value for {@link MutableSpan#localServiceName()}
     * @param ip default value for {@link MutableSpan#localIp()}
     * @param port default value for {@link MutableSpan#localPort()}
     */
    public abstract FirehoseHandler create(String serviceName, String ip, int port);

    /**
     * When true, all spans become real spans even if they aren't sampled remotely. This allows
     * firehose instances (such as metrics) to consider attributes that are not always visible
     * before-the-fact, such as http paths. Defaults to false and affects {@link
     * TraceContext#sampledLocal()}.
     *
     * @see FirehoseHandler#accept(TraceContext, MutableSpan)
     */
    public boolean alwaysSampleLocal() {
      return false;
    }
  }

  /**
   * Changes to the input span are visible by later firehose handlers. One reason to change the
   * input is to align tags, so that correlation occurs. For example, some may clean the tag
   * "http.path" knowing downstream handlers such as zipkin reporting have the same value.
   *
   * <p>Implementations should not hold a reference to it after this method returns. This is to
   * allow object recycling.
   */
  void accept(TraceContext context, MutableSpan span);
}

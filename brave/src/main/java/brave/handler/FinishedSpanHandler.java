package brave.handler;

import brave.Span;
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
 * @see #alwaysSampleLocal()
 */
public abstract class FinishedSpanHandler {
  /** Use to avoid comparing against null references */
  public static final FinishedSpanHandler NOOP = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      return true;
    }

    @Override public String toString() {
      return "NoopFinishedSpanHandler{}";
    }
  };

  /**
   * This is invoked after a span is finished, allowing data to be modified or reported out of
   * process. A return value of false means the span should be dropped completely from the stream.
   *
   * <p>Changes to the input span are visible by later finished span handlers. One reason to change the
   * input is to align tags, so that correlation occurs. For example, some may clean the tag
   * "http.path" knowing downstream handlers such as zipkin reporting have the same value.
   *
   * <p>Returning false is the same effect as if {@link Span#abandon()} was called. Implementations
   * should be careful when returning false as it can lead to broken traces. Acceptable use cases
   * are when the span is a leaf, for example a client call to an uninstrumented database, or a
   * server call which is known to terminate in-process (for example, health-checks). Prefer an
   * instrumentation policy approach to this mechanism as it results in less overhead.
   *
   * <p>Implementations should not hold a reference to it after this method returns. This is to
   * allow object recycling.
   *
   * @param context the trace context which is {@link TraceContext#sampled()} or {@link
   * TraceContext#sampledLocal()}. This includes identifiers and potentially {@link
   * TraceContext#extra() extra propagated data} such as extended sampling configuration.
   * @param span a mutable object including all data recorded with span apis. Modifications are
   * visible to later handlers, including Zipkin.
   * @return true retains the span, and should almost always be used. false drops the span, making
   * it invisible to later handlers such as Zipkin.
   */
  public abstract boolean handle(TraceContext context, MutableSpan span);

  /**
   * When true, all spans become real spans even if they aren't sampled remotely. This allows
   * firehose instances (such as metrics) to consider attributes that are not always visible
   * before-the-fact, such as http paths. Defaults to false and affects {@link
   * TraceContext#sampledLocal()}.
   *
   * @see #handle(TraceContext, MutableSpan)
   */
  public boolean alwaysSampleLocal() {
    return false;
  }
}

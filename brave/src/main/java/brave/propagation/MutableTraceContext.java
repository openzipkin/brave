package brave.propagation;

import brave.internal.Nullable;
import java.util.logging.Logger;

import static brave.propagation.SamplingFlags.FLAG_DEBUG;
import static brave.propagation.TraceContext.FLAG_SHARED;

// parseXXX methods package protected until we figure out if this is reusable enough to expose
public final class MutableTraceContext extends InternalMutableTraceContext {

  /**
   * Used to join an incoming trace. For example, by reading http headers.
   *
   * @see brave.Tracer#nextSpan(TraceContextOrSamplingFlags)
   */
  public interface Extractor<C> {

    /**
     * Returns either a trace context or sampling flags parsed from the carrier. If nothing was
     * parsable, sampling flags will be set to {@link SamplingFlags#EMPTY}.
     *
     * @param carrier holds propagation fields. For example, an incoming message or http request.
     */
    void extract(C carrier, MutableTraceContext state);
  }

  static final Logger LOG = Logger.getLogger(MutableTraceContext.class.getName());

  /** @see TraceContext#traceIdHigh() */
  public long traceIdHigh() {
    return traceIdHigh;
  }

  /** @see TraceContext#traceIdHigh() */
  public void traceIdHigh(long traceIdHigh) {
    this.traceIdHigh = traceIdHigh;
  }

  /** @see TraceContext#traceId() */
  public long traceId() {
    return traceId;
  }

  /** @see TraceContext#traceId() */
  public void traceId(long traceId) {
    this.traceId = traceId;
  }

  /** @see TraceContext#parentIdAsLong() */
  public long parentId() {
    return parentId;
  }

  /** @see TraceContext#parentIdAsLong() */
  public void parentId(long parentId) {
    this.parentId = parentId;
  }

  /** @see TraceContext#spanId() */
  public long spanId() {
    return spanId;
  }

  /** @see TraceContext#spanId() */
  public void spanId(long spanId) {
    this.spanId = spanId;
  }

  /** @see TraceContext#sampled() */
  @Nullable public Boolean sampled() {
    return SamplingFlags.sampled(flags);
  }

  /** @see TraceContext#sampled() */
  public void sampled(boolean sampled) {
    _sampled(sampled);
  }

  /** @see TraceContext#debug() */
  public boolean debug() {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }

  /** @see TraceContext#debug() */
  public void debug(boolean debug) {
    _debug(debug);
  }

  /** @see TraceContext#shared() */
  public boolean shared() {
    return (flags & FLAG_SHARED) == FLAG_SHARED;
  }

  /** @see TraceContext#shared() */
  public void shared(boolean shared) {
    _shared(shared);
  }

  /** Returns an {@linkplain #addExtra(Object) extra} of the given type if present or null if not. */
  public <T> T findExtra(Class<T> type) {
    return findExtra(extra, type);
  }

  /**
   * Adds iff there is not already an instance of the given type.
   *
   * @see TraceContext#extra()
   */
  public void addExtra(Object extra) {
    _addExtra(extra);
  }

  @Override Logger logger() {
    return LOG;
  }
} 



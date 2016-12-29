package brave.sampler;

import brave.propagation.TraceContext;

// TODO: replace with the existing sampler code once base code is merged.
public abstract class Sampler {

  public static final Sampler ALWAYS_SAMPLE = new Sampler() {
    @Override public boolean isSampled(long traceId) {
      return true;
    }

    @Override public String toString() {
      return "AlwaysSample";
    }
  };

  /**
   * Returns true if the trace ID should be measured.
   *
   * @param traceId the {@link TraceContext#traceId() first 64-bits of the trace ID.
   */
  public abstract boolean isSampled(long traceId);
}

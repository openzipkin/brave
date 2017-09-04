package brave.propagation;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Union type that contains either a trace context or sampling flags, but not both.
 *
 * <p>This is a port of {@code com.github.kristofa.brave.TraceData}, which served the same purpose.
 *
 * @see TraceContext.Extractor
 */
@Immutable
@AutoValue
public abstract class TraceContextOrSamplingFlags {

  /** When present, create the span via {@link brave.Tracer#joinSpan(TraceContext)} */
  @Nullable public abstract TraceContext context();

  /** When present, create the span via {@link brave.Tracer#newTrace(SamplingFlags)} */
  @Nullable public abstract SamplingFlags samplingFlags();

  public static TraceContextOrSamplingFlags create(SamplingFlags flags) {
    return new AutoValue_TraceContextOrSamplingFlags(null, flags);
  }

  public static TraceContextOrSamplingFlags create(TraceContext context) {
    return new AutoValue_TraceContextOrSamplingFlags(context, null);
  }

  public static TraceContextOrSamplingFlags create(TraceContext.Builder builder) {
    if (builder == null) throw new NullPointerException("builder == null");
    try {
      return new AutoValue_TraceContextOrSamplingFlags(builder.build(), null);
    } catch (IllegalStateException e) { // no trace IDs, but it might have sampling flags
      SamplingFlags flags = new SamplingFlags.Builder()
          .sampled(builder.sampled())
          .debug(builder.debug()).build();
      return new AutoValue_TraceContextOrSamplingFlags(null, flags);
    }
  }

  TraceContextOrSamplingFlags() { // no external implementations
  }
}

package brave.propagation;

import brave.internal.Nullable;
import com.google.auto.value.AutoValue;

/**
 * Union type that contains only one of trace context, trace ID context or sampling flags.
 *
 * <pre><ul>
 *   <li>If you have the trace and span ID, use {@link #create(TraceContext)}</li>
 *   <li>If you have only a trace ID, use {@link #create(TraceIdContext)}</li>
 *   <li>Otherwise, use {@link #create(SamplingFlags)}</li>
 * </ul></pre>
 *
 * <p>This is a port of {@code com.github.kristofa.brave.TraceData}, which served the same purpose.
 *
 * @see TraceContext.Extractor
 */
@AutoValue
//@Immutable
public abstract class TraceContextOrSamplingFlags {
  /** When present, create the span via {@link brave.Tracer#joinSpan(TraceContext)} */
  @Nullable public abstract TraceContext context();

  /** When present, create the span via {@link brave.Tracer#newTrace(TraceIdContext)} */
  @Nullable public abstract TraceIdContext traceIdContext();

  /** When present, create the span via {@link brave.Tracer#newTrace(SamplingFlags)} */
  @Nullable public abstract SamplingFlags samplingFlags();

  public static TraceContextOrSamplingFlags create(TraceContext context) {
    return new AutoValue_TraceContextOrSamplingFlags(context, null, null);
  }

  public static TraceContextOrSamplingFlags create(TraceIdContext traceIdContext) {
    return new AutoValue_TraceContextOrSamplingFlags(null, traceIdContext, null);
  }

  public static TraceContextOrSamplingFlags create(SamplingFlags flags) {
    return new AutoValue_TraceContextOrSamplingFlags(null, null, flags);
  }

  /** @deprecated call one of the other factory methods vs allocating an exception */
  @Deprecated
  public static TraceContextOrSamplingFlags create(TraceContext.Builder builder) {
    if (builder == null) throw new NullPointerException("builder == null");
    try {
      return new AutoValue_TraceContextOrSamplingFlags(builder.build(), null, null);
    } catch (IllegalStateException e) { // no trace IDs, but it might have sampling flags
      SamplingFlags flags = new SamplingFlags.Builder()
          .sampled(builder.sampled())
          .debug(builder.debug()).build();
      return new AutoValue_TraceContextOrSamplingFlags(null, null, flags);
    }
  }

  TraceContextOrSamplingFlags() { // no external implementations
  }
}

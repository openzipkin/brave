package brave.propagation;

import brave.Tracer;
import brave.internal.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Union type that contains only one of trace context, trace ID context or sampling flags. This type
 * is designed for use with {@link Tracer#nextSpan(TraceContextOrSamplingFlags)}.
 *
 * <p>Users should not create instances of this, rather use {@link TraceContext.Extractor} provided
 * by a {@link Propagation} implementation such as {@link Propagation#B3_STRING}.
 *
 * <p>Those implementing {@link Propagation} should use the following advice:
 * <pre><ul>
 *   <li>If you have the trace and span ID, use {@link #create(TraceContext)}</li>
 *   <li>If you have only a trace ID, use {@link #create(TraceIdContext)}</li>
 *   <li>Otherwise, use {@link #create(SamplingFlags)}</li>
 * </ul></pre>
 * <p>If your propagation implementation adds extra data, append it via {@link
 * Builder#addExtra(Object)}.
 *
 *
 * <p>This started as a port of {@code com.github.kristofa.brave.TraceData}, which served the same
 * purpose.
 *
 * @see TraceContext.Extractor
 */
//@Immutable
public final class TraceContextOrSamplingFlags {

  /** Returns {@link SamplingFlags#sampled()}, regardless of subtype. */
  @Nullable public Boolean sampled() {
    return value.sampled();
  }

  public TraceContextOrSamplingFlags sampled(@Nullable Boolean sampled) {
    switch (type) {
      case 1:
        return new TraceContextOrSamplingFlags(type, ((TraceContext) value).toBuilder()
            .sampled(sampled).build(), extra);
      case 2:
        return new TraceContextOrSamplingFlags(type, ((TraceIdContext) value).toBuilder()
            .sampled(sampled).build(), extra());
      case 3:
        return new TraceContextOrSamplingFlags(type, new SamplingFlags.Builder()
            .sampled(sampled).debug(value.debug()).build(), extra);
    }
    throw new AssertionError("programming error");
  }

  @Nullable public TraceContext context() {
    return type == 1 ? (TraceContext) value : null;
  }

  @Nullable public TraceIdContext traceIdContext() {
    return type == 2 ? (TraceIdContext) value : null;
  }

  @Nullable public SamplingFlags samplingFlags() {
    return type == 3 ? value : null;
  }

  /**
   * Non-empty when {@link #context} is null: A list of additional data extracted from the carrier.
   *
   * @see TraceContext#extra()
   */
  public final List<Object> extra() {
    return extra;
  }

  public final Builder toBuilder() {
    Builder result = new Builder();
    result.type = type;
    result.value = value;
    result.extra = extra;
    return result;
  }

  @Override
  public String toString() {
    return "{value=" + value + ", extra=" + extra + "}";
  }

  public static TraceContextOrSamplingFlags create(TraceContext context) {
    return new TraceContextOrSamplingFlags(1, context, Collections.emptyList());
  }

  public static TraceContextOrSamplingFlags create(TraceIdContext traceIdContext) {
    return new TraceContextOrSamplingFlags(2, traceIdContext, Collections.emptyList());
  }

  public static TraceContextOrSamplingFlags create(SamplingFlags flags) {
    return new TraceContextOrSamplingFlags(3, flags, Collections.emptyList());
  }

  /** @deprecated call one of the other factory methods vs allocating an exception */
  @Deprecated
  public static TraceContextOrSamplingFlags create(TraceContext.Builder builder) {
    if (builder == null) throw new NullPointerException("builder == null");
    try {
      return create(builder.build());
    } catch (IllegalStateException e) { // no trace IDs, but it might have sampling flags
      SamplingFlags flags = new SamplingFlags.Builder()
          .sampled(builder.sampled())
          .debug(builder.debug()).build();
      return create(flags);
    }
  }

  final int type;
  final SamplingFlags value;
  final List<Object> extra;

  TraceContextOrSamplingFlags(int type, SamplingFlags value, List<Object> extra) {
    this.type = type;
    this.value = value;
    this.extra = extra.isEmpty() ? extra : Collections.unmodifiableList(new ArrayList<>(extra));
  }

  public static final class Builder {
    int type;
    SamplingFlags value;
    List<Object> extra = Collections.emptyList();

    /** @see TraceContextOrSamplingFlags#extra() */
    public final Builder addExtra(Object extra) {
      if (extra == null) throw new NullPointerException("extra == null");
      if (!(this.extra instanceof ArrayList)) {
        this.extra = new ArrayList<>(this.extra); // make it mutable
      }
      this.extra.add(extra);
      return this;
    }

    public final TraceContextOrSamplingFlags build() {
      if (!extra.isEmpty() && type == 1) { // move extra to the trace context
        TraceContext context = (TraceContext) value;
        if (context.extra().isEmpty()) {
          context = context.toBuilder().extra(extra).build();
          return new TraceContextOrSamplingFlags(type, context, Collections.emptyList());
        }
        ArrayList<Object> copy = new ArrayList<>(extra);
        copy.addAll(context.extra());
        context = context.toBuilder().extra(copy).build();
        return new TraceContextOrSamplingFlags(type, context, Collections.emptyList());
      }
      // make sure the extra data is immutable and unmodifiable
      return new TraceContextOrSamplingFlags(type, value, extra);
    }

    Builder() { // no external implementations
    }
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TraceContextOrSamplingFlags)) return false;
    TraceContextOrSamplingFlags that = (TraceContextOrSamplingFlags) o;
    return (this.type == that.type)
        && (this.value.equals(that.value))
        && (this.extra.equals(that.extra));
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.type;
    h *= 1000003;
    h ^= this.value.hashCode();
    h *= 1000003;
    h ^= this.extra.hashCode();
    return h;
  }
}

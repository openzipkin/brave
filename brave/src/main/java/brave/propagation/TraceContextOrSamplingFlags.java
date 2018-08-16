package brave.propagation;

import brave.Tracer;
import brave.internal.Nullable;
import brave.internal.TraceContexts;
import java.util.ArrayList;
import java.util.List;

import static brave.internal.Lists.concatImmutableLists;
import static brave.internal.Lists.ensureImmutable;
import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static brave.internal.TraceContexts.contextWithExtra;
import static brave.internal.TraceContexts.contextWithFlags;
import static java.util.Collections.emptyList;

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
  public static final TraceContextOrSamplingFlags
      EMPTY = new TraceContextOrSamplingFlags(3, SamplingFlags.EMPTY, emptyList()),
      NOT_SAMPLED = new TraceContextOrSamplingFlags(3, SamplingFlags.NOT_SAMPLED, emptyList()),
      SAMPLED = new TraceContextOrSamplingFlags(3, SamplingFlags.SAMPLED, emptyList()),
      DEBUG = new TraceContextOrSamplingFlags(3, SamplingFlags.DEBUG, emptyList());

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Returns {@link SamplingFlags#sampled()}, regardless of subtype. */
  @Nullable public Boolean sampled() {
    return value.sampled();
  }

  /** @deprecated do not use object variant.. only set when you have a sampling decision */
  @Deprecated
  public TraceContextOrSamplingFlags sampled(@Nullable Boolean sampled) {
    if (sampled != null) return sampled(sampled.booleanValue());
    if (value.flags == 0) return this; // save effort if no change

    switch (type) {
      case 1:
        // use bitwise as trace context can have other flags like shared
        int flags = value.flags & ~(FLAG_SAMPLED_SET | FLAG_SAMPLED);
        TraceContext traceContext = contextWithFlags((TraceContext) value, flags);
        return new TraceContextOrSamplingFlags(type, traceContext, extra);
      case 2:
        return new TraceContextOrSamplingFlags(type, idContextWithFlags(0), extra);
      case 3:
        if (extra.isEmpty()) return EMPTY;
        return new TraceContextOrSamplingFlags(type, SamplingFlags.EMPTY, extra);
    }
    throw new AssertionError("programming error");
  }

  public TraceContextOrSamplingFlags sampled(boolean sampled) {
    int flags = TraceContexts.sampled(sampled, value.flags);
    if (flags == value.flags) return this; // save effort if no change

    switch (type) {
      case 1:
        TraceContext traceContext = contextWithFlags((TraceContext) value, flags);
        return new TraceContextOrSamplingFlags(type, traceContext, extra);
      case 2:
        TraceIdContext traceIdContext = idContextWithFlags(flags);
        return new TraceContextOrSamplingFlags(type, traceIdContext, extra);
      case 3:
        SamplingFlags samplingFlags = toSamplingFlags(sampled, flags);
        if (extra.isEmpty()) return create(samplingFlags);
        return new TraceContextOrSamplingFlags(type, samplingFlags, extra);
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
    return new TraceContextOrSamplingFlags(1, context, emptyList());
  }

  public static TraceContextOrSamplingFlags create(TraceIdContext traceIdContext) {
    return new TraceContextOrSamplingFlags(2, traceIdContext, emptyList());
  }

  public static TraceContextOrSamplingFlags create(SamplingFlags flags) {
    if (flags == SamplingFlags.DEBUG) return DEBUG;
    if (flags == SamplingFlags.EMPTY) return EMPTY;
    return flags == SamplingFlags.SAMPLED ? SAMPLED : NOT_SAMPLED;
  }

  public static TraceContextOrSamplingFlags create(@Nullable Boolean sampled, boolean debug) {
    if (debug) return DEBUG;
    if (sampled == null) return EMPTY;
    return sampled ? SAMPLED : NOT_SAMPLED;
  }

  final int type;
  final SamplingFlags value;
  final List<Object> extra;

  TraceContextOrSamplingFlags(int type, SamplingFlags value, List<Object> extra) {
    if (value == null) throw new NullPointerException("value == null");
    if (extra == null) throw new NullPointerException("extra == null");
    this.type = type;
    this.value = value;
    this.extra = extra;
  }

  public static final class Builder {
    int type;
    SamplingFlags value;
    List<Object> extra = emptyList();

    /** @see TraceContextOrSamplingFlags#context() */
    public final Builder context(TraceContext context) {
      if (context == null) throw new NullPointerException("context == null");
      type = 1;
      value = context;
      return this;
    }

    /** @see TraceContextOrSamplingFlags#traceIdContext() */
    public final Builder traceIdContext(TraceIdContext traceIdContext) {
      if (traceIdContext == null) throw new NullPointerException("traceIdContext == null");
      type = 2;
      value = traceIdContext;
      return this;
    }

    /** @see TraceContextOrSamplingFlags#samplingFlags() */
    public final Builder samplingFlags(SamplingFlags samplingFlags) {
      if (samplingFlags == null) throw new NullPointerException("samplingFlags == null");
      type = 3;
      value = samplingFlags;
      return this;
    }

    /**
     * Shares the input with the builder, replacing any current data in the builder.
     *
     * @see TraceContextOrSamplingFlags#extra()
     */
    public final Builder extra(List<Object> extra) {
      if (extra == null) throw new NullPointerException("extra == null");
      this.extra = extra; // sharing a copy in case it is immutable
      return this;
    }

    /** @see TraceContextOrSamplingFlags#extra() */
    public final Builder addExtra(Object extra) {
      if (extra == null) throw new NullPointerException("extra == null");
      if (!(this.extra instanceof ArrayList)) {
        this.extra = new ArrayList<>(this.extra); // make it mutable
      }
      this.extra.add(extra);
      return this;
    }

    /** Returns an immutable result from the values currently in the builder */
    public final TraceContextOrSamplingFlags build() {
      if (!extra.isEmpty() && type == 1) { // move extra to the trace context
        TraceContext context = (TraceContext) value;
        if (context.extra().isEmpty()) {
          context = contextWithExtra(context, ensureImmutable(extra));
        } else {
          context = contextWithExtra(context, concatImmutableLists(context.extra(), extra));
        }
        return new TraceContextOrSamplingFlags(type, context, emptyList());
      }
      // make sure the extra data is immutable and unmodifiable
      return new TraceContextOrSamplingFlags(type, value, ensureImmutable(extra));
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

  static SamplingFlags toSamplingFlags(boolean sampled, int flags) {
    if (flags == SamplingFlags.DEBUG.flags) {
      return SamplingFlags.DEBUG;
    } else if (sampled) {
      return SamplingFlags.SAMPLED;
    } else {
      return SamplingFlags.NOT_SAMPLED;
    }
  }

  TraceIdContext idContextWithFlags(int flags) {
    TraceIdContext traceIdContext = (TraceIdContext) value;
    return new TraceIdContext(flags, traceIdContext.traceIdHigh, traceIdContext.traceId);
  }
}

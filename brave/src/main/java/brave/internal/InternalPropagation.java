package brave.internal;

import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.List;

/**
 * Escalate internal APIs in {@code brave.propagation} so they can be used from outside packages.
 * The only implementation is in {@link SamplingFlags}.
 *
 * <p>Inspired by {@code okhttp3.internal.Internal}.
 */
public abstract class InternalPropagation {
  public static final int FLAG_SAMPLED = 1 << 1;
  public static final int FLAG_SAMPLED_SET = 1 << 2;
  public static final int FLAG_DEBUG = 1 << 3;
  public static final int FLAG_SHARED = 1 << 4;
  public static final int FLAG_SAMPLED_LOCAL = 1 << 5;

  public static InternalPropagation instance;

  public abstract int flags(SamplingFlags flags);

  public static int sampled(boolean sampled, int flags) {
    if (sampled) {
      flags |= FLAG_SAMPLED | FLAG_SAMPLED_SET;
    } else {
      flags |= FLAG_SAMPLED_SET;
      flags &= ~FLAG_SAMPLED;
    }
    return flags;
  }

  public abstract TraceContext newTraceContext(
      int flags,
      long traceIdHigh,
      long traceId,
      long parentId,
      long spanId,
      List<Object> extra
  );

  /** {@linkplain brave.propagation.TraceContext} is immutable so you need to read the result */
  public abstract TraceContext withExtra(TraceContext context, List<Object> immutableExtra);

  /** {@linkplain brave.propagation.TraceContext} is immutable so you need to read the result */
  public abstract TraceContext withFlags(TraceContext context, int flags);
}

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

  public static InternalPropagation instance;

  public abstract int flags(SamplingFlags flags);

  public abstract TraceContext newTraceContext(
      int flags,
      long traceIdHigh,
      long traceId,
      long parentId,
      long spanId,
      List<Object> extra
  );
}

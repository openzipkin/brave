package brave.internal;

import brave.propagation.TraceContext;
import java.util.List;

public final class TraceContexts {
  public static final int FLAG_SAMPLED = 1 << 1;
  public static final int FLAG_SAMPLED_SET = 1 << 2;
  public static final int FLAG_DEBUG = 1 << 3;
  public static final int FLAG_SHARED = 1 << 4;

  public static TraceContext contextWithFlags(TraceContext context, int flags) {
    return InternalPropagation.instance.newTraceContext(
        flags,
        context.traceIdHigh(),
        context.traceId(),
        context.parentIdAsLong(),
        context.spanId(),
        context.extra()
    );
  }

  public static TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return InternalPropagation.instance.newTraceContext(
        InternalPropagation.instance.flags(context),
        context.traceIdHigh(),
        context.traceId(),
        context.parentIdAsLong(),
        context.spanId(),
        Lists.ensureImmutable(extra)
    );
  }

  public static int sampled(boolean sampled, int flags) {
    if (sampled) {
      flags |= FLAG_SAMPLED | FLAG_SAMPLED_SET;
    } else {
      flags |= FLAG_SAMPLED_SET;
      flags &= ~FLAG_SAMPLED;
    }
    return flags;
  }

  TraceContexts() {
  }
}
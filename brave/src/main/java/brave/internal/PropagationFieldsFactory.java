package brave.internal;

import brave.propagation.TraceContext;
import java.util.List;

public abstract class PropagationFieldsFactory<P extends PropagationFields> extends
    ExtraFactory<P> {

  @Override protected abstract P create();

  @Override protected P createExtraAndClaim(long traceId, long spanId) {
    P result = create();
    result.tryToClaim(traceId, spanId);
    return result;
  }

  @Override protected P createExtraAndClaim(P existing, long traceId, long spanId) {
    P result = create(existing);
    result.tryToClaim(traceId, spanId);
    return result;
  }

  @Override protected boolean tryToClaim(P existing, long traceId, long spanId) {
    return existing.tryToClaim(traceId, spanId);
  }

  @Override protected void consolidate(P existing, P consolidated) {
    consolidated.putAllIfAbsent(existing);
  }

  // TODO: this is internal. If we ever expose it otherwise, we should use Lists.ensureImmutable
  @Override protected TraceContext contextWithExtra(TraceContext context,
      List<Object> immutableExtra) {
    return InternalPropagation.instance.withExtra(context, immutableExtra);
  }
}

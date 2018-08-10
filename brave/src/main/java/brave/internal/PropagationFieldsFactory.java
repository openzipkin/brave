package brave.internal;

import brave.propagation.MutableTraceContext;
import brave.propagation.TraceContext;

public abstract class PropagationFieldsFactory<P extends PropagationFields> {
  protected abstract Class<P> type();

  protected abstract P create();

  protected abstract P create(P parent);

  public final void decorate(MutableTraceContext mutableContext) {
    long traceId = mutableContext.traceId(), spanId = mutableContext.spanId();
    P existing = mutableContext.findExtra(type());
    if (existing == null) {
      P newFields = create();
      newFields.tryAssociate(traceId, spanId); // associate this with the new context
      mutableContext.addExtra(newFields);
      return;
    }

    // If this fields is unassociated (due to remote extraction), or it is the same span ID,
    // re-use the instance.
    if (existing.tryAssociate(traceId, spanId)) return;

    P newFields = create();
    newFields.putAllIfAbsent(existing);
    newFields.tryAssociate(traceId, spanId); // associate this with the new context
    mutableContext.removeExtra(existing);
    mutableContext.addExtra(newFields);
  }

  public final boolean isDecorated(TraceContext context) {
    P existing = context.findExtra(type());
    if (existing == null) return false;
    long traceId = context.traceId(), spanId = context.spanId();
    return existing.tryAssociate(traceId, spanId);
  }
}

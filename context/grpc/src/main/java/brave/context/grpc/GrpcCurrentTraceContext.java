package brave.context.grpc;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import io.grpc.Context;

/** Manages scope with a {@link Context} as opposed to a thread local. */
public final class GrpcCurrentTraceContext extends CurrentTraceContext {
  static final Context.Key<TraceContext> TRACE_CONTEXT_KEY = Context.key("zipkin-key");

  public static GrpcCurrentTraceContext create() {
    return new GrpcCurrentTraceContext();
  }

  @Override public TraceContext get() {
    return TRACE_CONTEXT_KEY.get(Context.current());
  }

  @Override public Scope newScope(TraceContext currentSpan) {
    final Context previous = Context.current().withValue(TRACE_CONTEXT_KEY, currentSpan).attach();
    return () -> Context.current().detach(previous);
  }

  GrpcCurrentTraceContext() {
  }
}

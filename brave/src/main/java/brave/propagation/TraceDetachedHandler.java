package brave.propagation;

public interface TraceDetachedHandler {
    void traceDetached(TraceContext context);
}

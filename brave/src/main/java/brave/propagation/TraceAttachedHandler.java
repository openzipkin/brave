package brave.propagation;

public interface TraceAttachedHandler {
    void traceAttached(TraceContext context);
}

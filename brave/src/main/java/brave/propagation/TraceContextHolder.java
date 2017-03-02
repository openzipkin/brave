package brave.propagation;

public class TraceContextHolder {
    private static final ThreadLocal<TraceContextItem> CURRENT_TRACE_CONTEXT = new ThreadLocal<>();

    public static TraceContext get() {
        TraceContextItem current = CURRENT_TRACE_CONTEXT.get();
        return current != null ? current.context : null;
    }

    public static void push(TraceContext context) {
        TraceContextItem parent = CURRENT_TRACE_CONTEXT.get();
        TraceContextItem child;
        if(parent != null) {
            parent.context.detachEvent();
            child = new TraceContextItem(context, parent);
        } else {
            child = new TraceContextItem(context);
        }
        child.context.attachEvent();
        CURRENT_TRACE_CONTEXT.set(child);
    }

    public static void pop() {
        TraceContextItem child = CURRENT_TRACE_CONTEXT.get();
        if(child != null) {
            child.context.detachEvent();
            if(child.parent != null) {
                TraceContextItem parent = child.parent;
                parent.context.attachEvent();
                CURRENT_TRACE_CONTEXT.set(parent);
            }
        }
    }

    private static class TraceContextItem {
        private final TraceContextItem parent;
        private final TraceContext context;

        private TraceContextItem(TraceContext context) {
            this(context, null);
        }

        private TraceContextItem(TraceContext context, TraceContextItem parent) {
            this.parent = parent;
            this.context = context;
        }
    }
}

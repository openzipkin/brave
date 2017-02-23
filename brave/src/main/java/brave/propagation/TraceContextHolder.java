package brave.propagation;

import brave.internal.Nullable;

public class TraceContextHolder {
    private static final ThreadLocal<TraceContextItem> CURRENT_TRACE_CONTEXT = new ThreadLocal<>();

    public static TraceContext get() {
        TraceContextItem current = CURRENT_TRACE_CONTEXT.get();
        return current != null ? current.getContext() : null;
    }

    static void pushAndMaybeGetParent(TraceContext context) {
        TraceContextItem parent = CURRENT_TRACE_CONTEXT.get();
        TraceContextItem child;
        if(parent != null) {
            parent.getContext().detach();
            child = new TraceContextItem(context, parent);
        } else {
            child = new TraceContextItem(context);
        }
        CURRENT_TRACE_CONTEXT.set(child);
    }

    static void popAndMaybeGetParent() {
        TraceContextItem child = CURRENT_TRACE_CONTEXT.get();
        if(child != null) {
            CURRENT_TRACE_CONTEXT.remove();
            if(child.getParent() != null) {
                TraceContextItem parent = child.getParent();
                parent.getContext().attach();
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

        @Nullable
        private TraceContextItem getParent() {
            return parent;
        }

        private TraceContext getContext() {
            return context;
        }
    }
}

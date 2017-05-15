package com.github.kristofa.brave;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerClientAndLocalSpanState} implementation that keeps trace state using {@link InheritableThreadLocal}
 * variables and provides local span inheritence from parent to children.
 * <p>
 * Important note: when using {@link InheritableServerClientAndLocalSpanState}, tracers must
 * {@link LocalTracer#finishSpan() finish spans} or clear the local span at
 * completion of the local trace span to avoid linking spans with incorrect
 * parents and avoid leaking spans and associated memory.
 * @deprecated Replaced by {@code brave.propagation.CurrentTraceContext}
 */
@Deprecated
public final class InheritableServerClientAndLocalSpanState implements ServerClientAndLocalSpanState {

    private final InheritableThreadLocal<ServerSpan> currentServerSpan =
            new InheritableThreadLocal<ServerSpan>() {
                @Override
                protected ServerSpan initialValue() {
                    return ServerSpan.EMPTY;
                }
            };

    private final InheritableThreadLocal<Span> currentClientSpan = new InheritableThreadLocal<Span>();

    private final InheritableThreadLocal<Deque<Span>> currentLocalSpan =
            new InheritableThreadLocal<Deque<Span>>() {
                @Override
                protected Deque<Span> initialValue() {
                    return new LinkedBlockingDeque<Span>();
                }
            };

    private final Endpoint endpoint;

    /**
     * @param endpoint Endpoint of the local service being traced.
     */
    public InheritableServerClientAndLocalSpanState(Endpoint endpoint) {
        this.endpoint = Util.checkNotNull(endpoint, "Endpoint must be specified.");
    }

    /** Never returns null: {@code setCurrentServerSpan(null)} coerces to {@link ServerSpan#EMPTY} */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan.get();
    }

    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        if (span == null) {
            currentServerSpan.remove();
        } else {
            currentServerSpan.set(span);
        }
    }

    @Override
    public Endpoint endpoint() {
        return endpoint;
    }

    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan.get();
    }

    @Override
    public void setCurrentClientSpan(final Span span) {
        currentClientSpan.set(span);
    }

    @Override
    public Boolean sample() {
        return getCurrentServerSpan().getSample();
    }

    @Override
    public Span getCurrentLocalSpan() {
        return currentLocalSpan.get().peekFirst();
    }

    /**
     * Sets the specified local span as the active span at the top of the
     * stack, or if the specified span is null, the top of the stack is popped.
     *
     * @param span Local span.
     */
    @Override
    public void setCurrentLocalSpan(Span span) {
        Deque<Span> deque = currentLocalSpan.get();
        if (span == null) {
            // pop to remove
            deque.pollFirst();
        } else {
            deque.addFirst(span);
        }
    }

    @Override
    public String toString() {
        return "InheritableServerClientAndLocalSpanState{"
                + "endpoint=" + endpoint + ", "
                + "currentLocalSpan=" + currentLocalSpan + ", "
                + "currentClientSpan=" + currentClientSpan + ", "
                + "currentServerSpan=" + currentServerSpan
                + "}";
    }
}

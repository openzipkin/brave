package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import javax.annotation.Nullable;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Allows binding span from request thread to a new executor thread.
 * <p/>
 * To be used in case you execute logic in new threads within you service and if you submit annotations or new requests from
 * those threads. The span state is bound to the request thread. When you start new threads it means that the span state that
 * was set in the request thread is not available in those new threads. The ServerSpanThreadBinder allows you to bind the
 * original span state to the new thread.
 * 
 * @author kristof
 * @deprecated Replaced by {@code brave.Tracer#withSpanInScope}
 */
public class ServerSpanThreadBinder extends CurrentSpan {

    private final ServerSpanState state;

    /**
     * Creates a new instance.
     *
     * @param state Server span state, should not be <code>null</code>
     */
    public ServerSpanThreadBinder(ServerSpanState state) {
        this.state = checkNotNull(state, "state");
    }

    /**
     * This should be called in the thread in which the request was received before executing code in new threads.
     * <p>
     * It returns the current server span which you can keep and bind to a new thread using
     * {@link ServerSpanThreadBinder#setCurrentSpan(ServerSpan)}.
     *
     * @see ServerSpanThreadBinder#setCurrentSpan(ServerSpan)
     * @return Returned Span can be bound to different executing threads.
     */
    @Nullable
    public ServerSpan getCurrentServerSpan() {
        return state.getCurrentServerSpan();
    }

    /**
     * Binds given span to current thread. This should typically be called when code is invoked in new thread to bind the
     * span from the thread in which we received the request to the new execution thread.
     *
     * @param span Span to bind to current execution thread. Should not be <code>null</code>.
     */
    public void setCurrentSpan(final ServerSpan span) {
        state.setCurrentServerSpan(span);
    }

    @Override Span get() {
        ServerSpan result = getCurrentServerSpan();
        return result != null ? result.getSpan(): null;
    }

    Boolean sampled() {
        ServerSpan result = getCurrentServerSpan();
        return result != null ? result.getSample(): null;
    }
}

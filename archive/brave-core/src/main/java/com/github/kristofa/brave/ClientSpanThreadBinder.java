package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Allows binding span from client request thread to a async callback thread that process the result.
 * <p/>
 * To be used for async client call the result of which is processed in a separate callback thread.
 * After calling {@link ClientTracer#startNewSpan(String)}, call getCurrentClientSpan() and save the result to pass to the
 * callback method (e.g., local final variable)
 * In the callback method, call {@link #setCurrentSpan} before calling {@link com.github.kristofa.brave.ClientTracer#setClientReceived()}
 * @author hzhao on 8/11/14.
 * @deprecated Replaced by {@code brave.Tracer#withSpanInScope}
 */
@Deprecated
public final class ClientSpanThreadBinder extends CurrentSpan {

    private final ClientSpanState state;

    /**
     * Creates a new instance.
     *
     * @param state client span state, should not be <code>null</code>
     */
    public ClientSpanThreadBinder(ClientSpanState state) {
        this.state = checkNotNull(state, "state");
    }

    /**
     * This should be called in the thread in which the client request made after starting new client span.
     * <p>
     * It returns the current client span which you can keep and bind to the callback thread
     * @see #setCurrentSpan(Span)
     * @return Returned Span can be bound to different callback thread.
     */
    public Span getCurrentClientSpan()
    {
        return state.getCurrentClientSpan();
    }

    /**
     * Binds given span to current thread. This should typically be called when code is invoked in async client callback
     * before the {@link ClientTracer#setClientReceived()}
     *
     * @param span Span to bind to current execution thread. Should not be <code>null</code>.
     */
    public void setCurrentSpan(Span span)
    {
        state.setCurrentClientSpan(span);
    }

    @Override Span get() {
        return getCurrentClientSpan();
    }
}

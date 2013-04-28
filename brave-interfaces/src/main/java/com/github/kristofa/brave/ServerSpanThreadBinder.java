package com.github.kristofa.brave;

/**
 * Allows binding span from request thread to a new executor thread.
 * <p/>
 * This should be used in case you execute logic in new threads in your service. In that case you have to bind the span state
 * from the request thread to your new threads to make sure we can still link spans correctly.
 * 
 * @author kristof
 */
public interface ServerSpanThreadBinder {

    /**
     * This should be called in the thread in which the request was received before executing code in new threads.
     * <p>
     * It returns the current server span which you can keep and bind to a new thread using
     * {@link ServerSpanThreadBinder#setCurrentSpan(Span)}.
     * 
     * @see ServerSpanThreadBinder#setCurrentSpan(Span)
     * @return Returned Span can be bound to different executing threads.
     */
    Span getCurrentServerSpan();

    /**
     * Binds given span to current thread. This should typically be called when code is invoked in new thread to bind the
     * span from the thread in which we received the request to the new execution thread.
     * 
     * @param span Span to bind to current execution thread. Should not be <code>null</code>.
     */
    void setCurrentSpan(final Span span);

}

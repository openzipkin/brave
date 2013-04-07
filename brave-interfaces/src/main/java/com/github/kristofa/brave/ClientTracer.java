package com.github.kristofa.brave;

/**
 * Used by a client that submits a new service request. At the moment the ClientTracer is used we can already be part of a
 * trace/span or we can be the first one to submit a request. </p> Depending on the implementation it can be that each
 * request is traced or it can be that only some requests are traced to avoid too much overhead. This is all managed in the
 * {@link ClientTracer} implementation. The user should not be aware.
 * 
 * @author kristof
 */
public interface ClientTracer extends AnnotationSubmitter {

    /**
     * Start a new span that will be bound to current thread. If another client request is already bound to current thread it
     * will be overridden.
     * 
     * @param requestName Request name. Should not be <code>null</code> or empty.
     * @return Span id for new request.
     */
    SpanId startNewSpan(final String requestName);

    /**
     * Sets 'client sent' event for current thread.
     */
    void setClientSent();

    /**
     * Sets the 'client received' event for current thread. This will also submit span because setting a client received
     * event means this span is finished.
     */
    void setClientReceived();

}

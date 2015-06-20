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
     * Start a new span for a new client request that will be bound to current thread. The ClientTracer can decide to return
     * <code>null</code> in case this request should not be traced (eg sampling).
     * 
     * @param requestName Request name. Should not be <code>null</code> or empty.
     * @return Span id for new request or <code>null</code> in case we should not trace this new client request.
     */
    SpanId startNewSpan(final String requestName);

    /**
     * Override the service name that will be submitted in the annotations.
     * <p/>
     * This should be set before submitting any annotations. So after invoking {@link ClientTracer#startNewSpan(String)} and
     * before {@link ClientTracer#setClientSent()}.
     * 
     * @param serviceName should be the same as the name of the service the client is calling.
     */
    void setCurrentClientServiceName(String serviceName);

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

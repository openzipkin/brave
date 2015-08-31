package com.github.kristofa.brave;

/**
 * A filter which can prevent that we trace all requests.
 * <p>
 * Using a TraceFilter we can introduce sampling to avoid performance overhead or avoid we reach our storage limitations.
 * 
 * @author kristof
 */
public interface TraceFilter {

    /**
     * Indicates if we should trace request with given name.
     * 
     * @param spanName Span name.
     * @return <code>true</code> in case we should trace this request, <code>false</code> in case we should not trace this
     *         request.
     */
    boolean trace(final long spanId, final String spanName);

    /**
     * Should be called when TraceFilter will not be used anymore. Used to close/clean resources.
     */
    void close();
}

package com.github.kristofa.brave;

/**
 * A filter which can prevent that we trace all requests.
 * <p>
 * Using a TraceFilter we can introduce sampling to avoid overhead be it CPU/time or storage overhead.
 * 
 * @author kristof
 */
public interface TraceFilter {

    /**
     * Indicates if we should trace request with given name.
     * 
     * @param requestName Name of request.
     * @return <code>true</code> in case we should trace this request, <code>false</code> in case we should not trace this
     *         request.
     */
    public boolean shouldTrace(final String requestName);

}

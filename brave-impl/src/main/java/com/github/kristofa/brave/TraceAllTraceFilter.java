package com.github.kristofa.brave;

/**
 * {@link TraceFilter} that indicates we should trace every request. So it does no filter.
 * 
 * @author adriaens
 */
class TraceAllTraceFilter implements TraceFilter {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTrace(final String requestName) {
        return true;
    }

}

package com.github.kristofa.brave;

/**
 * Collect spans. We can have implementations that simply log the collected spans or implementations that persist the spans
 * to a database, submit them to a service,...
 * 
 * @author kristof
 */
public interface SpanCollector {

    /**
     * Collect span.
     * 
     * @param span Span, should not be <code>null</code>.
     */
    void collect(final Span span);

}

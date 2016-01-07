package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * A {@link SpanCollector} implementation that does nothing with collected spans.
 * 
 * @author adriaens
 */
public class EmptySpanCollector implements SpanCollector {

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {
        // Nothing

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        // Nothing

    }
}

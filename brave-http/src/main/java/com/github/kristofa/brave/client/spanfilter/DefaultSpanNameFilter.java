package com.github.kristofa.brave.client.spanfilter;

/**
 * Default implementation of {@link SpanNameFilter} that doesn't filter.
 *
 * @author Pieter Cailliau (K-Jo)
 */
public class DefaultSpanNameFilter implements SpanNameFilter {

    /**
     * Returns the unfiltered Span name.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String filterSpanName(final String unfilteredSpanName) {
        return unfilteredSpanName;
    }
}
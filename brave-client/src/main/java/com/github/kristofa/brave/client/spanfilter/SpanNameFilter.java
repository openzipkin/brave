package com.github.kristofa.brave.client.spanfilter;

/**
 * A Span name filter can be used to filter parts out of span name so zipkin is able to group them more logical.
 * <p>
 * As an example: if you have context/path/id with id as a random UUID path variable, the default span collector will create
 * unique entries in zipkin for each webservice call.
 *
 * @author Pieter Cailliau (K-Jo)
 */
public interface SpanNameFilter {

    /**
     * Filter the span name.
     *
     * @param unfilteredSpanName {@link String}
     * @return the filtered span name.
     */
    String filterSpanName(String unfilteredSpanName);
}
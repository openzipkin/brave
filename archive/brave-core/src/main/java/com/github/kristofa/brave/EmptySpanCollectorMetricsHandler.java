package com.github.kristofa.brave;


/**
 * Empty implementation ignoring all events.
 */
public class EmptySpanCollectorMetricsHandler implements SpanCollectorMetricsHandler {

    @Override
    public void incrementAcceptedSpans(int quantity) {

    }

    @Override
    public void incrementDroppedSpans(int quantity) {

    }
}

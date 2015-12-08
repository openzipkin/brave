package com.github.kristofa.brave.scribe;

/**
 * Empty implementation ignoring all events.
 */
class EmptyScribeCollectorMetricsHandler implements ScribeCollectorMetricsHandler {

    @Override
    public void incrementAcceptedSpans(int quantity) {

    }

    @Override
    public void incrementDroppedSpans(int quantity) {

    }
}

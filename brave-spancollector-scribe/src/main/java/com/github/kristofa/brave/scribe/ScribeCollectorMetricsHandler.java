package com.github.kristofa.brave.scribe;

/**
 * Monitor {@link ScribeSpanCollector} by implementing reactions to these events, e.g. updating suitable metrics.
 *
 * See DropwizardMetricsScribeCollectorMetricsHandlerExample in test sources for an example.
 */
public interface ScribeCollectorMetricsHandler {

    /**
     * Called when spans are submitted to {@link ScribeSpanCollector} for processing.
     *
     * @param quantity the number of spans accepted.
     */
    void incrementAcceptedSpans(int quantity);

    /**
     * Called when spans become lost for any reason and won't be delivered to the target collector.
     *
     * @param quantity the number of spans dropped.
     */
    void incrementDroppedSpans(int quantity);

}

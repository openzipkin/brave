package com.github.kristofa.brave;


/**
 * Empty implementation ignoring all events.
 * @deprecated Replaced by {@code zipkin2.spanReporter.ReporterMetrics#NOOP_METRICS}
 */
@Deprecated
public class EmptySpanCollectorMetricsHandler implements SpanCollectorMetricsHandler {

    @Override
    public void incrementAcceptedSpans(int quantity) {

    }

    @Override
    public void incrementDroppedSpans(int quantity) {

    }
}

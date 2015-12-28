package com.github.kristofa.brave;

import com.codahale.metrics.MetricRegistry;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;

class DropwizardMetricsScribeCollectorMetricsHandlerExample implements SpanCollectorMetricsHandler {

    static final String ACCEPTED_METER = "tracing.collector.scribe.span.accepted";
    static final String DROPPED_METER = "tracing.collector.scribe.span.dropped";

    private final MetricRegistry registry;

    DropwizardMetricsScribeCollectorMetricsHandlerExample(MetricRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void incrementAcceptedSpans(int quantity) {
        registry.meter(ACCEPTED_METER).mark(quantity);
    }

    @Override
    public void incrementDroppedSpans(int quantity) {
        registry.meter(DROPPED_METER).mark(quantity);
    }

}

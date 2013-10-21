package com.github.kristofa.flume;

import java.io.Closeable;

import org.apache.commons.lang.Validate;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;

/**
 * Used to update metrics and report on them.
 * 
 * @author kristof
 */
class MetricReporter implements Closeable {

    private final MetricRegistry metricRegistry;
    private final ScheduledReporter reporter;
    private final HistogramBuilder histogramBuilder;

    /**
     * Creates a new instance.
     * 
     * @param metricRegistry Contains our metrics.
     * @param reporter Will report our metrics.
     * @param histogramBuilder Responsible for building new histograms.
     */
    public MetricReporter(final MetricRegistry metricRegistry, final ScheduledReporter reporter,
        final HistogramBuilder histogramBuilder) {
        Validate.notNull(metricRegistry);
        Validate.notNull(reporter);
        this.metricRegistry = metricRegistry;
        this.reporter = reporter;
        this.histogramBuilder = histogramBuilder;
    }

    /**
     * Updates metric with given name with given value.
     * 
     * @param name Metric name.
     * @param value Metric value.
     */
    public void update(final String name, final long value) {
        final Histogram histogram = getHistogram(name);
        histogram.update(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        reporter.close();
    }

    /**
     * Gets a Histogram.
     * 
     * @param name Name for metric.
     * @return Histogram.
     */
    private Histogram getHistogram(final String name) {
        Histogram histogram = metricRegistry.getHistograms().get(name);
        if (histogram == null) {
            histogram = histogramBuilder.buildHistogram();
            metricRegistry.register(name, histogram);
        }
        return histogram;
    }

}

package com.github.kristofa.flume;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;

/**
 * Creates a {@link HistogramBuilder} that will create histograms configured using {@link ExponentiallyDecayingReservoir}.
 * 
 * @author kristof
 */
class ExponentiallyDecayingHistogramBuilder implements HistogramBuilder {

    private int size;
    private double alpha;

    /**
     * Create a new instance that will create default values for size and alpha values for
     * {@link ExponentiallyDecayingReservoir}.
     * 
     * @param metricRegistry MetricRegistry.
     * @param reporter Reporter.
     */
    ExponentiallyDecayingHistogramBuilder() {
    }

    /**
     * Create a new instance with custom values for size and alpha values for {@link ExponentiallyDecayingReservoir}.
     * 
     * @param size the number of samples to keep in the sampling reservoir
     * @param alpha the exponential decay factor; the higher this is, the more biased the reservoir will be towards newer
     *            values
     */
    ExponentiallyDecayingHistogramBuilder(final int size, final double alpha) {
        this.size = size;
        this.alpha = alpha;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Histogram buildHistogram() {
        ExponentiallyDecayingReservoir reservoir;
        if (size == 0) {
            reservoir = new ExponentiallyDecayingReservoir();
        } else {
            reservoir = new ExponentiallyDecayingReservoir(size, alpha);
        }
        return new Histogram(reservoir);
    }

}

package com.github.kristofa.flume;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingWindowReservoir;

/**
 * {@link HistogramBuilder} that builds histogram using {@link SlidingWindowReservoir}.
 * 
 * @author adriaens
 */
class SlidingWindowHistogramBuilder implements HistogramBuilder {

    private final int nrOfMeasurements;

    /**
     * Creates a new instance.
     * 
     * @param nrOfMeasurements Number of measurements we should keep into account.
     */
    public SlidingWindowHistogramBuilder(final int nrOfMeasurements) {
        this.nrOfMeasurements = nrOfMeasurements;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Histogram buildHistogram() {

        final SlidingWindowReservoir slidingWindowReservoir = new SlidingWindowReservoir(nrOfMeasurements);
        return new Histogram(slidingWindowReservoir);
    }
}

package com.github.kristofa.flume;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.UniformReservoir;

/**
 * Creates histograms with the {@link UniformReservoir}.
 * 
 * @author kristof
 */
class UniformHistogramBuilder implements HistogramBuilder {

    private int nrOfSamples;

    /**
     * Creates Histograms using {@link UniformReservoir} with default number of samples.
     */
    public UniformHistogramBuilder() {

    }

    /**
     * Creates Histograms using {@link UniformReservoir} with custom number of samples.
     * 
     * @param nrOfSamples
     */
    public UniformHistogramBuilder(final int nrOfSamples) {
        this.nrOfSamples = nrOfSamples;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Histogram buildHistogram() {

        UniformReservoir reservoir;
        if (nrOfSamples == 0) {
            reservoir = new UniformReservoir();
        } else {
            reservoir = new UniformReservoir(nrOfSamples);
        }
        return new Histogram(reservoir);
    }
}

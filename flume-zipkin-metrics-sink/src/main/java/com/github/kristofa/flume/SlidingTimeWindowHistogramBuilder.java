package com.github.kristofa.flume;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowReservoir;

/**
 * Histogram builder that creates histograms with {@link SlidingTimeWindowReservoir}.
 * 
 * @author kristof
 */
class SlidingTimeWindowHistogramBuilder implements HistogramBuilder {

    private final long windowInSeconds;

    /**
     * Create a new instance.
     * 
     * @param windowInSeconds Window in seconds.
     */
    SlidingTimeWindowHistogramBuilder(final long windowInSeconds) {
        this.windowInSeconds = windowInSeconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Histogram buildHistogram() {
        final SlidingTimeWindowReservoir reservoir = new SlidingTimeWindowReservoir(windowInSeconds, TimeUnit.SECONDS);
        return new Histogram(reservoir);
    }

}

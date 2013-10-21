package com.github.kristofa.flume;

import com.codahale.metrics.Histogram;

/**
 * Builds {@link Histogram histograms}.
 * 
 * @author kristof
 */
interface HistogramBuilder {

    /**
     * Returns a new Histogram.
     * 
     * @return A new histogram.
     */
    Histogram buildHistogram();

}

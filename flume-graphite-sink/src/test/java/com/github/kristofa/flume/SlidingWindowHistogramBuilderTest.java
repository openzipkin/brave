package com.github.kristofa.flume;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import com.codahale.metrics.Histogram;

public class SlidingWindowHistogramBuilderTest {

    @Test
    public void testBuildHistogram() {
        final SlidingWindowHistogramBuilder histogramBuilder = new SlidingWindowHistogramBuilder(3000);
        final Histogram histogram1 = histogramBuilder.buildHistogram();
        assertNotNull(histogram1);
        final Histogram histogram2 = histogramBuilder.buildHistogram();
        assertNotNull(histogram2);
        assertNotSame(histogram1, histogram2);
    }

}

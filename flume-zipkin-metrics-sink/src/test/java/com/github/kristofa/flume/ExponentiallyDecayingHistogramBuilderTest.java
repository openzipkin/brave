package com.github.kristofa.flume;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import com.codahale.metrics.Histogram;

public class ExponentiallyDecayingHistogramBuilderTest {

    @Test
    public void testBuildHistogramDefaultParams() {
        final ExponentiallyDecayingHistogramBuilder builder = new ExponentiallyDecayingHistogramBuilder();
        final Histogram histogram1 = builder.buildHistogram();
        assertNotNull(histogram1);
        final Histogram histogram2 = builder.buildHistogram();
        assertNotNull(histogram2);
        assertNotSame(histogram1, histogram2);
    }

    @Test
    public void testBuildHistogramSpecificParams() {
        final ExponentiallyDecayingHistogramBuilder builder = new ExponentiallyDecayingHistogramBuilder(1000, 0.5);
        final Histogram histogram1 = builder.buildHistogram();
        assertNotNull(histogram1);
        final Histogram histogram2 = builder.buildHistogram();
        assertNotNull(histogram2);
        assertNotSame(histogram1, histogram2);
    }

}

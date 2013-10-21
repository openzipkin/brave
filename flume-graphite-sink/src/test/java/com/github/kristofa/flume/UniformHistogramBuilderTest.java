package com.github.kristofa.flume;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import com.codahale.metrics.Histogram;

public class UniformHistogramBuilderTest {

    @Test
    public void testBuildHistogramDefaultParams() {
        final UniformHistogramBuilder builder = new UniformHistogramBuilder();
        final Histogram histogram1 = builder.buildHistogram();
        assertNotNull(histogram1);
        final Histogram histogram2 = builder.buildHistogram();
        assertNotNull(histogram2);
        assertNotSame(histogram1, histogram2);
    }

    @Test
    public void testBuildHistogramCustomParams() {
        final UniformHistogramBuilder builder = new UniformHistogramBuilder(1024);
        final Histogram histogram1 = builder.buildHistogram();
        assertNotNull(histogram1);
        final Histogram histogram2 = builder.buildHistogram();
        assertNotNull(histogram2);
        assertNotSame(histogram1, histogram2);
    }

}

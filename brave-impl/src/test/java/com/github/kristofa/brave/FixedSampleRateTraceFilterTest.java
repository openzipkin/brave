package com.github.kristofa.brave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FixedSampleRateTraceFilterTest {

    @Test
    public void testSampleRateZero() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(0);
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
    }

    @Test
    public void testSampleRateNegative() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(-1);
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
    }

    @Test
    public void testSampleRateOne() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(1);
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
    }

    @Test
    public void testSampleRateBiggerThanOne() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(3);
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
        assertTrue(fixedSampleRateTraceFilter.shouldTrace(null));
        assertFalse(fixedSampleRateTraceFilter.shouldTrace(null));
    }

}

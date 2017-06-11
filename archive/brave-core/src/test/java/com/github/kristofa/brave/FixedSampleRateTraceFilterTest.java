package com.github.kristofa.brave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FixedSampleRateTraceFilterTest {

    private static final long SPAN_ID = 454464;

    @Test
    public void testSampleRateZero() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(0);
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
    }

    @Test
    public void testSampleRateNegative() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(-1);
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
    }

    @Test
    public void testSampleRateOne() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(1);
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
    }

    @Test
    public void testSampleRateBiggerThanOne() {
        final FixedSampleRateTraceFilter fixedSampleRateTraceFilter = new FixedSampleRateTraceFilter(3);
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertTrue(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
        assertFalse(fixedSampleRateTraceFilter.trace(SPAN_ID, null));
    }

}

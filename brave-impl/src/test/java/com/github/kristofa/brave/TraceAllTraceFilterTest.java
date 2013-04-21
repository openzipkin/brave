package com.github.kristofa.brave;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TraceAllTraceFilterTest {

    private final static String REQUEST_NAME = "request";

    @Test
    public void testShouldTrace() {
        final TraceAllTraceFilter traceAllTraceFilter = new TraceAllTraceFilter();
        assertTrue(traceAllTraceFilter.shouldTrace(REQUEST_NAME));
    }

}

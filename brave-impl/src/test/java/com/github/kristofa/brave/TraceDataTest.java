package com.github.kristofa.brave;


import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class TraceDataTest {

    private static final boolean SAMPLE = true;
    private static final SpanId SPAN_ID = new SpanId(3454, 3353, Optional.of(34343l));


    @Test
    public void testDefaultTraceData() {
        TraceData defaultTraceData = new TraceData.Builder().build();
        assertEquals(Optional.empty(), defaultTraceData.getSample());
        assertEquals(Optional.empty(), defaultTraceData.getSpanId());
    }

    @Test
    public void testTraceDataConstruction() {
        TraceData traceData = new TraceData.Builder().sample(SAMPLE).spanId(SPAN_ID).build();
        assertEquals(Optional.of(SAMPLE), traceData.getSample());
        assertEquals(Optional.of(SPAN_ID), traceData.getSpanId());
    }

}

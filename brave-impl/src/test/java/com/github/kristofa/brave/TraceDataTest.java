package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class TraceDataTest {

    private static final long TRACE_ID = 3454;
    private static final long SPAN_ID = 3353;
    private static final boolean SAMPLE = true;
    private static final long PARENT_SPAN_ID = 34343;
    private TraceData traceData;

    @Test
    public void testDefaultTraceData() {
        TraceData defaultTraceData = new TraceData.Builder().build();
        assertEquals(Optional.empty(), defaultTraceData.getParentSpanId());
        assertEquals(Optional.empty(), defaultTraceData.getSample());
        assertEquals(Optional.empty(), defaultTraceData.getSpanId());
        assertEquals(Optional.empty(), defaultTraceData.getTraceId());
    }

    @Test
    public void testTraceDataConstruction() {
        TraceData defaultTraceData = new TraceData.Builder().parentSpanId(PARENT_SPAN_ID).sample(SAMPLE).spanId(SPAN_ID).traceId(TRACE_ID).build();
        assertEquals(Optional.of(PARENT_SPAN_ID), defaultTraceData.getParentSpanId());
        assertEquals(Optional.of(SAMPLE), defaultTraceData.getSample());
        assertEquals(Optional.of(SPAN_ID), defaultTraceData.getSpanId());
        assertEquals(Optional.of(TRACE_ID), defaultTraceData.getTraceId());
    }

}

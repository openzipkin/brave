package com.github.kristofa.brave;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TraceDataTest {

    private static final boolean SAMPLE = true;
    private static final SpanId SPAN_ID =
        SpanId.builder().traceId(3454).spanId(3353).parentId(34343L).build();


    @Test
    public void testDefaultTraceData() {
        TraceData defaultTraceData = TraceData.builder().build();
        assertNull(defaultTraceData.getSample());
        assertNull(defaultTraceData.getSpanId());
    }

    @Test
    public void testTraceDataConstruction() {
        TraceData traceData = TraceData.builder().sample(SAMPLE).spanId(SPAN_ID).build();
        assertEquals(SAMPLE, traceData.getSample());
        assertEquals(SPAN_ID, traceData.getSpanId());
    }

}

package com.github.kristofa.brave;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TraceDataTest {

    private static final SpanId SPAN_ID =
        SpanId.builder().traceId(3454).spanId(3353).parentId(34343L).sampled(true).build();


    @Test
    public void testEmptyTraceData() {
        assertNull(TraceData.EMPTY.getSample());
        assertNull(TraceData.EMPTY.getSpanId());
    }

    @Test
    public void testTraceDataConstruction() {
        TraceData traceData = TraceData.create(SPAN_ID);
        assertEquals(true, traceData.getSample());
        assertEquals(SPAN_ID, traceData.getSpanId());
    }

}

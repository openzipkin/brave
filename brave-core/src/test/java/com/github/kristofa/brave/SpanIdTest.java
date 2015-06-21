package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpanIdTest {

    private final static long TRACEID = 10;
    private final static long SPANID = 11;
    private final static Long PARENT_SPANID = new Long(12);

    private SpanId spanId;

    @Before
    public void setup() {
        spanId = SpanId.create(TRACEID, SPANID, PARENT_SPANID);
    }

    @Test
    public void testSpanIdNullParentId() {
        final SpanId spanId2 = SpanId.create(TRACEID, SPANID, null);
        assertNull(spanId2.getParentSpanId());
    }

    @Test
    public void testGetTraceId() {
        assertEquals(TRACEID, spanId.getTraceId());
    }

    @Test
    public void testGetSpanId() {
        assertEquals(SPANID, spanId.getSpanId());
    }

    @Test
    public void testGetParentSpanId() {
        assertEquals(PARENT_SPANID, spanId.getParentSpanId());
    }

    @Test
    public void testGetOptionalParentSpanId() {
        assertEquals(PARENT_SPANID, spanId.getParentSpanId());
    }


    @Test
    public void testHashCode() {
        final SpanId equalSpanId = SpanId.create(TRACEID, SPANID, PARENT_SPANID);
        assertEquals(spanId.hashCode(), equalSpanId.hashCode());
    }

    @Test
    public void testEquals() {
        assertTrue(spanId.equals(spanId));
        assertFalse(spanId.equals(null));
        assertFalse(spanId.equals(new String()));

        final SpanId equalSpanId = SpanId.create(TRACEID, SPANID, PARENT_SPANID);
        assertTrue(spanId.equals(equalSpanId));

        final SpanId nonEqualSpanId = SpanId.create(TRACEID + 1, SPANID, PARENT_SPANID);
        final SpanId nonEqualSpanId2 = SpanId.create(TRACEID, SPANID + 1, PARENT_SPANID);
        final SpanId nonEqualSpanId3 = SpanId.create(TRACEID, SPANID, PARENT_SPANID + 1);

        assertFalse(spanId.equals(nonEqualSpanId));
        assertFalse(spanId.equals(nonEqualSpanId2));
        assertFalse(spanId.equals(nonEqualSpanId3));
    }

    @Test
    public void testToString() {
        assertEquals(
            "[trace id: " + TRACEID + ", span id: " + SPANID + ", parent span id: " + PARENT_SPANID
            + "]",
            spanId.toString());
    }

    @Test
    public void testToStringNullParent() {
        final SpanId spanId2 = SpanId.create(TRACEID, SPANID, null);
        assertEquals("[trace id: " + TRACEID + ", span id: " + SPANID + ", parent span id: null]", spanId2.toString());
    }
}

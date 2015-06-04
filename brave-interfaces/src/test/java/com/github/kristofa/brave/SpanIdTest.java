package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class SpanIdTest {

    private final static long TRACEID = 10;
    private final static long SPANID = 11;
    private final static Long PARENT_SPANID = new Long(12);

    private SpanId spanIdImpl;

    @Before
    public void setup() {
        spanIdImpl = new SpanId(TRACEID, SPANID, Optional.of(PARENT_SPANID));
    }

    @Test
    public void testSpanIdNullParentId() {
        final SpanId spanIdImpl2 = new SpanId(TRACEID, SPANID, Optional.empty());
        assertNull(spanIdImpl2.getParentSpanId());
    }

    @Test
    public void testGetTraceId() {
        assertEquals(TRACEID, spanIdImpl.getTraceId());
    }

    @Test
    public void testGetSpanId() {
        assertEquals(SPANID, spanIdImpl.getSpanId());
    }

    @Test
    public void testGetParentSpanId() {
        assertEquals(PARENT_SPANID, spanIdImpl.getParentSpanId());
    }

    @Test
    public void testGetOptionalParentSpanId() {
        assertEquals(Optional.of(PARENT_SPANID), spanIdImpl.getOptionalParentSpanId());
    }


    @Test
    public void testHashCode() {
        final SpanId equalSpanId = new SpanId(TRACEID, SPANID, Optional.of(PARENT_SPANID));
        assertEquals(spanIdImpl.hashCode(), equalSpanId.hashCode());
    }

    @Test
    public void testEquals() {
        assertTrue(spanIdImpl.equals(spanIdImpl));
        assertFalse(spanIdImpl.equals(null));
        assertFalse(spanIdImpl.equals(new String()));

        final SpanId equalSpanId = new SpanId(TRACEID, SPANID, Optional.of(PARENT_SPANID));
        assertTrue(spanIdImpl.equals(equalSpanId));

        final SpanId nonEqualSpanId = new SpanId(TRACEID + 1, SPANID, Optional.of(PARENT_SPANID));
        final SpanId nonEqualSpanId2 = new SpanId(TRACEID, SPANID + 1, Optional.of(PARENT_SPANID));
        final SpanId nonEqualSpanId3 = new SpanId(TRACEID, SPANID, Optional.of(PARENT_SPANID + 1));

        assertFalse(spanIdImpl.equals(nonEqualSpanId));
        assertFalse(spanIdImpl.equals(nonEqualSpanId2));
        assertFalse(spanIdImpl.equals(nonEqualSpanId3));
    }



    @Test
    public void testToString() {
        assertEquals("[trace id: " + TRACEID + ", span id: " + SPANID + ", parent span id: Optional[" + PARENT_SPANID + "]]",
                spanIdImpl.toString());
    }

    @Test
    public void testToStringNullParent() {
        final SpanId spanIdImpl2 = new SpanId(TRACEID, SPANID, Optional.empty());
        assertEquals("[trace id: " + TRACEID + ", span id: " + SPANID + ", parent span id: Optional.empty]", spanIdImpl2.toString());
    }
}

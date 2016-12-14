package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ServerSpanTest {
    private static final long TRACE_ID = 1;
    private static final SpanId SPAN_ID = SpanId.builder().traceId(TRACE_ID).spanId(2).parentId(3L).build();
    private static final String NAME = "name";

    private ServerSpan serverSpan;

    @Before
    public void setup() {
        serverSpan = ServerSpan.create(SPAN_ID, NAME);
    }

    @Test
    public void testGetSpan() {
        final Span span = serverSpan.getSpan();
        assertNotNull(span);
        assertEquals(TRACE_ID, span.getTrace_id());
        assertEquals(SPAN_ID.spanId, span.getId());
        assertEquals(SPAN_ID.parentId, span.getParent_id().longValue());
        assertEquals(NAME, span.getName());
        assertTrue(span.getAnnotations().isEmpty());
        assertTrue(span.getBinary_annotations().isEmpty());
    }

    @Test
    public void testGetSpan_128() {
        serverSpan = ServerSpan.create(SPAN_ID.toBuilder().traceIdHigh(5).build(), NAME);

        Span span = serverSpan.getSpan();
        assertEquals(5, span.getTrace_id_high());
        assertEquals(TRACE_ID, span.getTrace_id());
    }

    @Test
    public void testGetSample() {
        assertTrue(serverSpan.getSample());
    }

    @Test
    public void testEqualsObject() {

        ServerSpan equalServerSpan = ServerSpan.create(SPAN_ID, NAME);
        assertTrue(serverSpan.equals(equalServerSpan));
    }

    @Test
    public void testHashCode() {
        ServerSpan equalServerSpan = ServerSpan.create(SPAN_ID, NAME);
        Assert.assertEquals(serverSpan.hashCode(), equalServerSpan.hashCode());
    }

}

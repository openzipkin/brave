package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ServerSpanTest {
    private static final long TRACE_ID = 1;
    private static final SpanId SPAN_ID =
        SpanId.builder().sampled(true).traceId(TRACE_ID).spanId(2).parentId(3L).build();

    private ServerSpan serverSpan = ServerSpan.create(Brave.toSpan(SPAN_ID));

    @Test
    public void testGetSpan() {
        final Span span = serverSpan.getSpan();
        assertNotNull(span);
        assertEquals(TRACE_ID, span.getTrace_id());
        assertEquals(SPAN_ID.spanId, span.getId());
        assertEquals(SPAN_ID.parentId, span.getParent_id().longValue());
        assertTrue(span.getAnnotations().isEmpty());
        assertTrue(span.getBinary_annotations().isEmpty());
    }

    @Test
    public void testGetSpan_128() {
        serverSpan = ServerSpan.create(Brave.toSpan(SPAN_ID.toBuilder().traceIdHigh(5).build()));

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

        ServerSpan equalServerSpan = ServerSpan.create(Brave.toSpan(SPAN_ID));
        assertTrue(serverSpan.equals(equalServerSpan));
    }

    @Test
    public void testHashCode() {
        ServerSpan equalServerSpan = ServerSpan.create(Brave.toSpan(SPAN_ID));
        Assert.assertEquals(serverSpan.hashCode(), equalServerSpan.hashCode());
    }

    @Test
    public void createUnsampled() {
        serverSpan = ServerSpan.create(Brave.toSpan(SPAN_ID.toBuilder().sampled(false).build()));
        assertEquals(serverSpan, ServerSpan.NOT_SAMPLED);
    }
}

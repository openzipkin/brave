package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ServerSpanTest {

    private static final long TRACE_ID = 1;
    private static final long SPAN_ID = 2;
    private static final Long PARENT_SPAN_ID = Long.valueOf(3);
    private static final String NAME = "name";
    private ServerSpan serverSpan;

    @Before
    public void setup() {
        serverSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, NAME);
    }

    @Test
    public void testServerSpanSampleNull() {
        final ServerSpan serverSpan = ServerSpan.create(null);
        assertNull(serverSpan.getSample());
        assertNull(serverSpan.getSpan());
    }

    @Test
    public void testServerSpanSampleFalse() {
        final ServerSpan serverSpan = ServerSpan.create(false);
        assertFalse(serverSpan.getSample());
        assertNull(serverSpan.getSpan());
    }

    @Test
    public void testGetSpan() {
        final Span span = serverSpan.getSpan();
        assertNotNull(span);
        assertEquals(TRACE_ID, span.getTrace_id());
        assertEquals(SPAN_ID, span.getId());
        assertEquals(PARENT_SPAN_ID.longValue(), span.getParent_id());
        assertEquals(NAME, span.getName());
        assertNull(span.getAnnotations());
        assertNull(span.getBinary_annotations());

    }

    @Test
    public void testIncThreadDuration() {
        serverSpan.incThreadDuration(10);
        Assert.assertEquals(10, serverSpan.getThreadDuration());
        serverSpan.incThreadDuration(5);
        Assert.assertEquals(15, serverSpan.getThreadDuration());
    }

    @Test
    public void testGetThreadDuration() {
        Assert.assertEquals(0, serverSpan.getThreadDuration());
    }

    @Test
    public void testGetSample() {
        assertTrue(serverSpan.getSample());
    }

    @Test
    public void testEqualsObject() {

        final ServerSpan equalServerSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, NAME);
        assertTrue(serverSpan.equals(equalServerSpan));
    }

    @Test
    public void testHashCode() {
        final ServerSpan equalServerSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, NAME);
        Assert.assertEquals(serverSpan.hashCode(), equalServerSpan.hashCode());
    }

}

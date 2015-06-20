package com.github.kristofa.brave.client.spanfilter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DefaultSpanNameFilterImplTest {

    private static final String SPAN = "/path/path/1a2a3";

    @Test
    public void testDefaulSpanNameFilter() {
        final DefaultSpanNameFilterImpl defaultSpanNameFilterImpl = new DefaultSpanNameFilterImpl();

        final String filterSpanName = defaultSpanNameFilterImpl.filterSpanName(SPAN);

        assertEquals(SPAN, filterSpanName);
    }
}
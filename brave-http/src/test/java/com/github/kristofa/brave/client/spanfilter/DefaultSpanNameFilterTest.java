package com.github.kristofa.brave.client.spanfilter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DefaultSpanNameFilterTest {

    private static final String SPAN = "/path/path/1a2a3";

    @Test
    public void testDefaulSpanNameFilter() {
        final DefaultSpanNameFilter defaultSpanNameFilter = new DefaultSpanNameFilter();

        final String filterSpanName = defaultSpanNameFilter.filterSpanName(SPAN);

        assertEquals(SPAN, filterSpanName);
    }
}
package com.github.kristofa.brave.client.spanfilter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpanNameFilterNumericTest {

    private static final String SPAN_WITH = "path/path/1s23q";
    private static final String SPAN_WITH_RESULT = "path/path/<numeric>";
    private static final String SPAN_WITHOUT = "path/path";

    @Test
    public void testSpanNameFilterNumeric() {
        final SpanNameFilterNumeric spanNameFilterNumeric = new SpanNameFilterNumeric();

        final String filterSpanName = spanNameFilterNumeric.filterSpanName(SPAN_WITHOUT);
        assertEquals(SPAN_WITHOUT, filterSpanName);
        final String filterSpanNameNumeric = spanNameFilterNumeric.filterSpanName(SPAN_WITH);
        assertEquals(SPAN_WITH_RESULT, filterSpanNameNumeric);
    }
}
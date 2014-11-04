package com.github.kristofa.brave.client.spanfilter;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class PatternBasedSpanNameFilterImplTest {

    @Test
    public void testMultiplePatterns() throws Exception {
        final String add = "/api/{something}/{else}/add";
        final String remove = "/api/{something}/{else}/remove";
        final String anythingElse = "/api/{anythingelse}";
        final PatternBasedSpanNameFilterImpl filter
                = new PatternBasedSpanNameFilterImpl(Lists.newArrayList(add, remove, anythingElse));

        assertEquals(add, filter.filterSpanName("/api/clients/relationships/add"));
        assertEquals(remove, filter.filterSpanName("/api/science/math/remove"));
        assertEquals(anythingElse, filter.filterSpanName("/api/set/up/us/the/bomb"));
    }

    @Test
    public void testNoMatch() throws Exception {
        final String add = "/api/{something}/{else}/add";
        final PatternBasedSpanNameFilterImpl filter
                = new PatternBasedSpanNameFilterImpl(Lists.newArrayList(add));

        assertEquals(PatternBasedSpanNameFilterImpl.DEFAULT_SPAN_NAME, filter.filterSpanName("/api/nomatch"));
    }

    @Test
    public void testHandlesNull() throws Exception {
        final PatternBasedSpanNameFilterImpl filter = new PatternBasedSpanNameFilterImpl(null);
        assertEquals(PatternBasedSpanNameFilterImpl.DEFAULT_SPAN_NAME, filter.filterSpanName("/api/whatever"));
    }

    @Test
    public void testHandlesEmpty() throws Exception {
        final String undef = "UNDEF";
        final PatternBasedSpanNameFilterImpl filter = new PatternBasedSpanNameFilterImpl(Collections.<String>emptyList(), undef);

        assertEquals(undef, filter.filterSpanName("/api/anything/at/all"));
    }

    @Test
    public void testHandlesNullPatterns() throws Exception {
        final String undef = "not-defined";
        final PatternBasedSpanNameFilterImpl filter = new PatternBasedSpanNameFilterImpl(Lists.<String>newArrayList(null, null), undef);

        assertEquals(undef, filter.filterSpanName("/api/not-a-match"));
    }

    @Test
    public void testCaseInsensitive() throws Exception {
        final String updatePattern = "/api/{userid}/update";
        final PatternBasedSpanNameFilterImpl filter = new PatternBasedSpanNameFilterImpl(Lists.newArrayList(updatePattern));

        assertEquals(updatePattern, filter.filterSpanName("/api/12/update"));
        assertEquals(updatePattern, filter.filterSpanName("/api/12/UPDATE"));
    }
}
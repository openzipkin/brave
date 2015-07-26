package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TraceFiltersTest {

    @Test(expected = NullPointerException.class)
    public void testTraceFilters() {
        new TraceFilters(null);
    }

    @Test
    public void testGetTraceFilters() {
        final TraceFilter mockFilter = mock(TraceFilter.class);
        final TraceFilter mockFilter2 = mock(TraceFilter.class);

        final List<TraceFilter> mockFilters = Arrays.asList(mockFilter, mockFilter2);

        final TraceFilters traceFilters = new TraceFilters(mockFilters);
        final List<TraceFilter> returnedList = traceFilters.getTraceFilters();
        assertNotNull(returnedList);
        assertEquals(2, returnedList.size());
        assertSame(mockFilter, returnedList.get(0));
        assertSame(mockFilter2, returnedList.get(1));

    }

}

package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class EmptySpanCollectorTest {

    @Test
    public void testCollect() {
        final EmptySpanCollector emptySpanCollector = new EmptySpanCollector();
        final Span mockSpan = mock(Span.class);
        emptySpanCollector.collect(mockSpan);
        verifyNoMoreInteractions(mockSpan);
    }

}

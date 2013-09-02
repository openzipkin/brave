package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class EmptySpanCollectorImplTest {

    @Test
    public void testCollect() {
        final EmptySpanCollectorImpl emptySpanCollectorImpl = new EmptySpanCollectorImpl();
        final Span mockSpan = mock(Span.class);
        emptySpanCollectorImpl.collect(mockSpan);
        verifyNoMoreInteractions(mockSpan);
    }

}

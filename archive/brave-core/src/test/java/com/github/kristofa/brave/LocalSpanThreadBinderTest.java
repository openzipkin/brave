package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LocalSpanThreadBinderTest {

    private LocalSpanState mockLocalSpanState;
    private Span mockSpan;
    private LocalSpanThreadBinder binder;

    @Before
    public void setup() {
        mockLocalSpanState = mock(LocalSpanState.class);
        binder = new LocalSpanThreadBinder(mockLocalSpanState);
        mockSpan = mock(Span.class);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new LocalSpanThreadBinder(null);
    }

    @Test
    public void testGetCurrentLocalSpanNullSpan() {
        assertNull(binder.getCurrentLocalSpan());
        verify(mockLocalSpanState).getCurrentLocalSpan();
        verifyNoMoreInteractions(mockLocalSpanState);
    }

    @Test
    public void testGetCurrentLocalSpan() {
        when(mockLocalSpanState.getCurrentLocalSpan()).thenReturn(mockSpan);
        assertSame(mockSpan, binder.getCurrentLocalSpan());
        verify(mockLocalSpanState).getCurrentLocalSpan();
        verifyNoMoreInteractions(mockLocalSpanState);
    }

    @Test
    public void testSetCurrentSpanNull() {
        binder.setCurrentSpan(null);
        verify(mockLocalSpanState).setCurrentLocalSpan(null);
        verifyNoMoreInteractions(mockLocalSpanState);
    }

    @Test
    public void testSetCurrentSpan() {
        binder.setCurrentSpan(mockSpan);
        verify(mockLocalSpanState).setCurrentLocalSpan(mockSpan);
        verifyNoMoreInteractions(mockLocalSpanState);
    }

}

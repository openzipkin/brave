package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class ClientSpanThreadBinderTest {

    private ClientSpanState mockClientSpanState;
    private Span mockSpan;
    private ClientSpanThreadBinder binder;

    @Before
    public void setup() {
        mockClientSpanState = mock(ClientSpanState.class);
        binder = new ClientSpanThreadBinder(mockClientSpanState);
        mockSpan = mock(Span.class);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ClientSpanThreadBinder(null);
    }

    @Test
    public void testGetCurrentClientSpanNullSpan() {
        assertNull(binder.getCurrentClientSpan());
        verify(mockClientSpanState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockClientSpanState);
    }

    @Test
    public void testGetCurrentClientSpan() {
        when(mockClientSpanState.getCurrentClientSpan()).thenReturn(mockSpan);
        assertSame(mockSpan, binder.getCurrentClientSpan());
        verify(mockClientSpanState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockClientSpanState);
    }

    @Test
    public void testSetCurrentSpanNull() {
        binder.setCurrentSpan(null);
        verify(mockClientSpanState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockClientSpanState);
    }

    @Test
    public void testSetCurrentSpan() {
        binder.setCurrentSpan(mockSpan);
        verify(mockClientSpanState).setCurrentClientSpan(mockSpan);
        verifyNoMoreInteractions(mockClientSpanState);
    }

}

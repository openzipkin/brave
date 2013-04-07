package com.github.kristofa.brave;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class ServerSpanThreadBinderImplTest {

    private ServerSpanState mockServerSpanState;
    private Span mockSpan;
    private ServerSpanThreadBinderImpl binder;

    @Before
    public void setup() {
        mockServerSpanState = mock(ServerSpanState.class);
        binder = new ServerSpanThreadBinderImpl(mockServerSpanState);
        mockSpan = mock(Span.class);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ServerSpanThreadBinderImpl(null);
    }

    @Test
    public void testGetCurrentServerSpanNullServerSpan() {
        assertNull(binder.getCurrentServerSpan());
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState);
    }

    @Test
    public void testGetCurrentServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        assertSame(mockSpan, binder.getCurrentServerSpan());
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState);
    }

    @Test
    public void testSetCurrentSpanNull() {
        binder.setCurrentSpan(null);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState);
    }

    @Test
    public void testSetCurrentSpan() {
        binder.setCurrentSpan(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(mockSpan);
        verifyNoMoreInteractions(mockServerSpanState);
    }

}

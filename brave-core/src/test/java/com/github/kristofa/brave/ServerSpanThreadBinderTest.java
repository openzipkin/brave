package com.github.kristofa.brave;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class ServerSpanThreadBinderTest {

    private ServerSpanState mockServerSpanState;
    private ServerSpan mockSpan;
    private ServerSpanThreadBinder binder;

    @Before
    public void setup() {
        mockServerSpanState = mock(ServerSpanState.class);
        binder = new ServerSpanThreadBinder(mockServerSpanState);
        mockSpan = mock(ServerSpan.class);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ServerSpanThreadBinder(null);
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
    public void testSetCurrentSpanEmpty() {
        binder.setCurrentSpan(ServerSpan.EMPTY);
        verify(mockServerSpanState).setCurrentServerSpan(ServerSpan.EMPTY);
        verifyNoMoreInteractions(mockServerSpanState);
    }

    @Test
    public void testSetCurrentSpan() {
        binder.setCurrentSpan(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(mockSpan);
        verifyNoMoreInteractions(mockServerSpanState);
    }

}

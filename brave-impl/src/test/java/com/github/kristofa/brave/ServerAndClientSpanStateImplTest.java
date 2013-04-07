package com.github.kristofa.brave;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerAndClientSpanStateImplTest {

    private ServerAndClientSpanStateImpl serverAndClientSpanState;
    private Span mockSpan;
    private EndPoint mockEndPoint;

    @Before
    public void setup() {
        serverAndClientSpanState = new ServerAndClientSpanStateImpl();
        mockSpan = mock(Span.class);
        mockEndPoint = mock(EndPoint.class);
    }

    @After
    public void tearDown() {
        serverAndClientSpanState.setCurrentClientSpan(null);
        serverAndClientSpanState.setCurrentServerSpan(null);
        serverAndClientSpanState.setEndPoint(null);
        serverAndClientSpanState.setTracing(true);
    }

    @Test
    public void testGetAndSetCurrentServerSpan() {
        assertNull(serverAndClientSpanState.getCurrentServerSpan());
        serverAndClientSpanState.setCurrentServerSpan(mockSpan);
        assertSame(mockSpan, serverAndClientSpanState.getCurrentServerSpan());
        assertNull("Should not have been modified.", serverAndClientSpanState.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetShouldTrace() {
        assertTrue(serverAndClientSpanState.shouldTrace());
        serverAndClientSpanState.setTracing(false);
        assertFalse(serverAndClientSpanState.shouldTrace());
    }

    @Test
    public void testGetAndSetEndPoint() {
        assertNull(serverAndClientSpanState.getEndPoint());
        serverAndClientSpanState.setEndPoint(mockEndPoint);
        assertSame(mockEndPoint, serverAndClientSpanState.getEndPoint());
    }

    @Test
    public void testGetAndSetCurrentClientSpan() {
        assertNull(serverAndClientSpanState.getCurrentClientSpan());
        serverAndClientSpanState.setCurrentClientSpan(mockSpan);
        assertSame(mockSpan, serverAndClientSpanState.getCurrentClientSpan());
        assertNull("Should not have been modified.", serverAndClientSpanState.getCurrentServerSpan());
    }

}

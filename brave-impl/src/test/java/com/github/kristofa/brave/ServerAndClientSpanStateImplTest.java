package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

public class ServerAndClientSpanStateImplTest {

    private static final long DURATION1 = 10;
    private static final long DURATION2 = 30;
    private ServerAndClientSpanStateImpl serverAndClientSpanState;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndPoint;

    @Before
    public void setup() {
        serverAndClientSpanState = new ServerAndClientSpanStateImpl();
        mockServerSpan = mock(ServerSpan.class);
        mockSpan = mock(Span.class);
        mockEndPoint = mock(Endpoint.class);
    }

    @After
    public void tearDown() {
        serverAndClientSpanState.setCurrentClientSpan(null);
        serverAndClientSpanState.setCurrentServerSpan(null);
        serverAndClientSpanState.setEndPoint(null);
    }

    @Test
    public void testGetAndSetCurrentServerSpan() {
        assertEquals(new ServerSpanImpl(null), serverAndClientSpanState.getCurrentServerSpan());
        serverAndClientSpanState.setCurrentServerSpan(mockServerSpan);
        assertSame(mockServerSpan, serverAndClientSpanState.getCurrentServerSpan());
        assertNull("Should not have been modified.", serverAndClientSpanState.getCurrentClientSpan());
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
        assertEquals("Should not have been modified.", new ServerSpanImpl(null),
            serverAndClientSpanState.getCurrentServerSpan());
    }

    @Test
    public void testGetAndSetServerSpanThreadDuration() {
        assertEquals(0, serverAndClientSpanState.getServerSpanThreadDuration());
        serverAndClientSpanState.incrementServerSpanThreadDuration(DURATION1);
        assertEquals(DURATION1, serverAndClientSpanState.getServerSpanThreadDuration());
        serverAndClientSpanState.incrementServerSpanThreadDuration(DURATION2);
        assertEquals(DURATION1 + DURATION2, serverAndClientSpanState.getServerSpanThreadDuration());
    }

}

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

public class ThreadLocalServerAndClientSpanStateTest {

    private static final long DURATION1 = 10;
    private static final long DURATION2 = 30;
    private ThreadLocalServerAndClientSpanState serverAndClientSpanState;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint;

    @Before
    public void setup() {
        serverAndClientSpanState = new ThreadLocalServerAndClientSpanState();
        mockServerSpan = mock(ServerSpan.class);
        mockSpan = mock(Span.class);
        mockEndpoint = mock(Endpoint.class);
    }

    @After
    public void tearDown() {
        serverAndClientSpanState.setCurrentClientSpan(null);
        serverAndClientSpanState.setCurrentServerSpan(null);
        serverAndClientSpanState.setServerEndpoint(null);
    }

    @Test
    public void testGetAndSetCurrentServerSpan() {
        assertEquals(ServerSpan.create(null), serverAndClientSpanState.getCurrentServerSpan());
        serverAndClientSpanState.setCurrentServerSpan(mockServerSpan);
        assertSame(mockServerSpan, serverAndClientSpanState.getCurrentServerSpan());
        assertNull("Should not have been modified.", serverAndClientSpanState.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetEndpoint() {
        assertNull(serverAndClientSpanState.getServerEndpoint());
        serverAndClientSpanState.setServerEndpoint(mockEndpoint);
        assertSame(mockEndpoint, serverAndClientSpanState.getServerEndpoint());
    }

    @Test
    public void testGetAndSetCurrentClientSpan() {
        assertNull(serverAndClientSpanState.getCurrentClientSpan());
        serverAndClientSpanState.setCurrentClientSpan(mockSpan);
        assertSame(mockSpan, serverAndClientSpanState.getCurrentClientSpan());
        assertEquals("Should not have been modified.", ServerSpan.create(null),
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

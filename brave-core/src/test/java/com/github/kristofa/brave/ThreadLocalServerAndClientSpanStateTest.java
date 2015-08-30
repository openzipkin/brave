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

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ThreadLocalServerAndClientSpanStateTest {

    private static final short PORT = 80;
    private static final String SERVICE_NAME = "service";
    private static final long DURATION1 = 10;
    private static final long DURATION2 = 30;
    private ThreadLocalServerAndClientSpanState serverAndClientSpanState;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint;
    private InetAddress address;

    @Before
    public void setup() throws UnknownHostException {
        address = InetAddress.getByName("192.168.0.1");
        serverAndClientSpanState = new ThreadLocalServerAndClientSpanState(address, PORT, SERVICE_NAME);
        mockServerSpan = mock(ServerSpan.class);
        mockSpan = mock(Span.class);
        mockEndpoint = mock(Endpoint.class);
    }

    @After
    public void tearDown() {
        serverAndClientSpanState.setCurrentClientSpan(null);
        serverAndClientSpanState.setCurrentServerSpan(null);
    }

    @Test
    public void testGetAndSetCurrentServerSpan() {
        assertEquals(ServerSpan.create(null), serverAndClientSpanState.getCurrentServerSpan());
        serverAndClientSpanState.setCurrentServerSpan(mockServerSpan);
        assertSame(mockServerSpan, serverAndClientSpanState.getCurrentServerSpan());
        assertNull("Should not have been modified.", serverAndClientSpanState.getCurrentClientSpan());
    }

    @Test
    public void testGetAndSetCurrentClientSpan() {
        assertNull(serverAndClientSpanState.getCurrentClientSpan());
        serverAndClientSpanState.setCurrentClientSpan(mockSpan);
        assertSame(mockSpan, serverAndClientSpanState.getCurrentClientSpan());
        assertEquals("Should not have been modified.", ServerSpan.create(null),
            serverAndClientSpanState.getCurrentServerSpan());
    }

}

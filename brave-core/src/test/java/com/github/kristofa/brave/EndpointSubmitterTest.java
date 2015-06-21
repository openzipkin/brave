package com.github.kristofa.brave;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;

public class EndpointSubmitterTest {

    private final static String IP = "10.0.0.1";
    private final static int PORT = 8080;
    private final static String SERVICE_NAME = "serviceName";

    private ServerAndClientSpanState mockState;
    private Endpoint mockEndpoint;
    private EndpointSubmitter endpointSubmitter;

    @Before
    public void setUp() {
        mockState = mock(ServerAndClientSpanState.class);
        mockEndpoint = new Endpoint();
        endpointSubmitter = new EndpointSubmitter(mockState);
    }

    @Test(expected = NullPointerException.class)
    public void testEndpointSubmitterNullState() {
        new EndpointSubmitter(null);
    }

    @Test
    public void testSubmit() {
        endpointSubmitter.submit(IP, PORT, SERVICE_NAME);
        final Endpoint expectedEndpoint = new Endpoint(ipAddressToInt(IP), (short)PORT, SERVICE_NAME);
        verify(mockState).setServerEndpoint(expectedEndpoint);
        verifyNoMoreInteractions(mockState);
    }

    @Test
    public void testEndpointSubmitted() {

        when(mockState.getServerEndpoint()).thenReturn(mockEndpoint);
        assertTrue(endpointSubmitter.endpointSubmitted());
        verify(mockState).getServerEndpoint();
        verifyNoMoreInteractions(mockState);
    }

    private int ipAddressToInt(final String ip) {
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(ip);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
    }

}

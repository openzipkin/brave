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

public class EndPointSubmitterImplTest {

    private final static String IP = "10.0.0.1";
    private final static int PORT = 8080;
    private final static String SERVICE_NAME = "serviceName";

    private CommonSpanState mockState;
    private Endpoint mockEndPoint;
    private EndPointSubmitterImpl endPointSubmitter;

    @Before
    public void setUp() {
        mockState = mock(CommonSpanState.class);
        mockEndPoint = new Endpoint();
        endPointSubmitter = new EndPointSubmitterImpl(mockState);
    }

    @Test(expected = NullPointerException.class)
    public void testEndPointSubmitterImplNullState() {
        new EndPointSubmitterImpl(null);
    }

    @Test
    public void testSubmit() {
        endPointSubmitter.submit(IP, PORT, SERVICE_NAME);
        final Endpoint expectedEndPoint = new Endpoint(ipAddressToInt(IP), (short)PORT, SERVICE_NAME);
        verify(mockState).setEndPoint(expectedEndPoint);
        verifyNoMoreInteractions(mockState);
    }

    @Test
    public void testEndPointSubmitted() {

        when(mockState.getEndPoint()).thenReturn(mockEndPoint);
        assertTrue(endPointSubmitter.endPointSubmitted());
        verify(mockState).getEndPoint();
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

package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

public class BraveTest {

    private static final String SERVICE_NAME = "service";
    private static final int PORT = 80;
    private static final String IP = "10.0.1.5";
    private SpanCollector mockSpanCollector;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
    }

    @Test
    public void testSetGetEndPoint() {
        final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();
        endPointSubmitter.submit(IP, PORT, SERVICE_NAME);
        final EndPointImpl expectedEndPoint = new EndPointImpl(IP, PORT, SERVICE_NAME);
        assertEquals(expectedEndPoint, Brave.getEndPointSubmitter().getEndPoint());
    }

    @Test
    public void testGetLoggingSpanCollector() {
        final SpanCollector loggingSpanCollector = Brave.getLoggingSpanCollector();
        assertNotNull(loggingSpanCollector);
        assertTrue(loggingSpanCollector instanceof LoggingSpanCollectorImpl);
    }

    @Test
    public void testGetClientTracerCustomCollector() {
        assertNotNull(Brave.getClientTracer(mockSpanCollector));
    }

    @Test
    public void testGetServerTracerCustomCollector() {
        assertNotNull(Brave.getServerTracer(mockSpanCollector));
    }

    @Test
    public void testGetServerSpanAnnotationSubmitter() {
        assertNotNull(Brave.getServerSpanAnnotationSubmitter());
    }

    @Test
    public void testGetServerSpanThreadBinder() {
        assertNotNull(Brave.getServerSpanThreadBinder());
    }

}

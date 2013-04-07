package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

public class BraveTest {

    private SpanCollector mockSpanCollector;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
    }

    @Test
    public void testSetGetEndPoint() {
        final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();
        endPointSubmitter.submit(10, 80, "service");
        final EndPointImpl expectedEndPoint = new EndPointImpl(10, 80, "service");
        assertEquals(expectedEndPoint, Brave.getEndPointSubmitter().getEndPoint());
    }

    @Test
    public void testGetClientTracer() {
        assertNotNull(Brave.getClientTracer());
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
    public void testGetServerTracer() {
        assertNotNull(Brave.getServerTracer());
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

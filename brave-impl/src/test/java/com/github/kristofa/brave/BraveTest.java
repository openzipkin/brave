package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class BraveTest {

    private static final String SERVICE_NAME = "service";
    private static final int PORT = 80;
    private static final String IP = "10.0.1.5";
    private SpanCollector mockSpanCollector;
    private TraceFilter mockTraceFilter;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        mockTraceFilter = mock(TraceFilter.class);
    }

    @Test
    public void testSetGetEndPoint() {
        final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();
        endPointSubmitter.submit(IP, PORT, SERVICE_NAME);
        assertTrue(Brave.getEndPointSubmitter().endPointSubmitted());
    }

    @Test
    public void testGetLoggingSpanCollector() {
        final SpanCollector loggingSpanCollector = Brave.getLoggingSpanCollector();
        assertNotNull(loggingSpanCollector);
        assertTrue(loggingSpanCollector instanceof LoggingSpanCollectorImpl);
    }

    public void test() {
        final TraceFilter traceAllTraceFilter = Brave.getTraceAllTraceFilter();
        assertNotNull(traceAllTraceFilter);
        assertTrue(traceAllTraceFilter instanceof TraceAllTraceFilter);
    }

    @Test
    public void testGetClientTracer() {
        final ClientTracer clientTracer = Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertNotNull(clientTracer);
        assertTrue("We expect instance of ClientTracerImpl", clientTracer instanceof ClientTracerImpl);
        final ClientTracerImpl clientTracerImpl = (ClientTracerImpl)clientTracer;
        assertSame("ClientTracer should be configured with the spancollector we submitted.", mockSpanCollector,
            clientTracerImpl.getSpanCollector());
        assertSame("ClientTracer should be configured with the tracefilter we submitted.", mockTraceFilter, clientTracerImpl
            .getTraceFilters().get(0));

        final ClientTracerImpl secondClientTracerImpl =
            (ClientTracerImpl)Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertSame("It is important that each client tracer we get shares same state.",
            clientTracerImpl.getServerAndClientSpanState(), secondClientTracerImpl.getServerAndClientSpanState());
    }

    @Test
    public void testGetServerTracer() {
        final ServerTracer serverTracer = Brave.getServerTracer(mockSpanCollector);
        assertNotNull(serverTracer);
        assertTrue(serverTracer instanceof ServerTracerImpl);
        final ServerTracerImpl serverTracerImpl = (ServerTracerImpl)serverTracer;
        assertSame(mockSpanCollector, serverTracerImpl.getSpanCollector());

        final ServerTracerImpl secondServerTracer = (ServerTracerImpl)Brave.getServerTracer(mockSpanCollector);
        assertSame("It is important that each client tracer we get shares same state.",
            serverTracerImpl.getServerSpanState(), secondServerTracer.getServerSpanState());
    }

    @Test
    public void testStateBetweenServerAndClient() {
        final ClientTracerImpl clientTracer =
            (ClientTracerImpl)Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        final ServerTracerImpl serverTracer = (ServerTracerImpl)Brave.getServerTracer(mockSpanCollector);

        assertSame("Client and server tracers should share same state.", clientTracer.getServerAndClientSpanState(),
            serverTracer.getServerSpanState());

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

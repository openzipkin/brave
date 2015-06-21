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
    public void testSetGetEndpoint() {
        final EndpointSubmitter endpointSubmitter = Brave.getEndpointSubmitter();
        endpointSubmitter.submit(IP, PORT, SERVICE_NAME);
        assertTrue(Brave.getEndpointSubmitter().endpointSubmitted());
    }

    @Test
    public void testGetClientTracer() {
        final ClientTracer clientTracer = Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertNotNull(clientTracer);
        assertTrue("We expect instance of ClientTracer", clientTracer instanceof ClientTracer);
        assertSame("ClientTracer should be configured with the spancollector we submitted.", mockSpanCollector,
            clientTracer.spanCollector());
        assertSame("ClientTracer should be configured with the tracefilter we submitted.",
                   mockTraceFilter, clientTracer
                       .traceFilters().get(0));

        final ClientTracer secondClientTracer =
            Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertSame("It is important that each client tracer we get shares same state.",
                   clientTracer.spanAndEndpoint().state(), secondClientTracer.spanAndEndpoint().state());
    }

    @Test
    public void testGetServerTracer() {
        final ServerTracer serverTracer = Brave.getServerTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertNotNull(serverTracer);
        assertSame(mockSpanCollector, serverTracer.spanCollector());
        assertSame("ServerTracer should be configured with the tracefilter we submitted.", mockTraceFilter, serverTracer
            .traceFilters().get(0));

        final ServerTracer secondServerTracer =
            Brave.getServerTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        assertSame("It is important that each client tracer we get shares same state.",
                   serverTracer.spanAndEndpoint().state(), secondServerTracer.spanAndEndpoint().state());
    }

    @Test
    public void testStateBetweenServerAndClient() {
        final ClientTracer clientTracer =
            Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        final ServerTracer serverTracer =
            Brave.getServerTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));

        assertSame("Client and server tracers should share same state.", clientTracer.spanAndEndpoint().state(),
            serverTracer.spanAndEndpoint().state());

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

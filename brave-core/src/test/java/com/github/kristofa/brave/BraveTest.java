package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class BraveTest {

    private SpanCollector mockSpanCollector;
    private TraceFilter mockTraceFilter;
    private Brave brave;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        mockTraceFilter = mock(TraceFilter.class);
        // -1062731775 = 192.168.0.1
        final Brave.Builder builder = new Brave.Builder(-1062731775, 8080, "unknown");
        brave = builder.spanCollector(mockSpanCollector).traceFilters(Arrays.asList(mockTraceFilter)).build();
    }

    @Test
    public void testGetClientTracer() {
        final ClientTracer clientTracer = brave.clientTracer();
        assertNotNull(clientTracer);
        assertTrue("We expect instance of ClientTracer", clientTracer instanceof ClientTracer);
        assertSame("ClientTracer should be configured with the spancollector we submitted.", mockSpanCollector,
            clientTracer.spanCollector());
        assertSame("ClientTracer should be configured with the tracefilter we submitted.",
                mockTraceFilter, clientTracer
                        .traceFilters().get(0));

        final ClientTracer secondClientTracer =
            brave.clientTracer();
        assertSame("It is important that each client tracer we get shares same state.",
                   clientTracer.spanAndEndpoint().state(), secondClientTracer.spanAndEndpoint().state());
    }

    @Test
    public void testGetServerTracer() {
        final ServerTracer serverTracer = brave.serverTracer();
        assertNotNull(serverTracer);
        assertSame(mockSpanCollector, serverTracer.spanCollector());
        assertSame("ServerTracer should be configured with the tracefilter we submitted.", mockTraceFilter, serverTracer
            .traceFilters().get(0));

        final ServerTracer secondServerTracer =
            brave.serverTracer();
        assertSame("It is important that each client tracer we get shares same state.",
                   serverTracer.spanAndEndpoint().state(), secondServerTracer.spanAndEndpoint().state());
    }

    @Test
    public void testStateBetweenServerAndClient() {
        final ClientTracer clientTracer =
            brave.clientTracer();
        final ServerTracer serverTracer =
            brave.serverTracer();

        assertSame("Client and server tracers should share same state.", clientTracer.spanAndEndpoint().state(),
            serverTracer.spanAndEndpoint().state());

    }

    @Test
    public void testGetServerSpanAnnotationSubmitter() {
        assertNotNull(brave.serverSpanAnnotationSubmitter());
    }

    @Test
    public void testGetServerSpanThreadBinder() {
        assertNotNull(brave.serverSpanThreadBinder());
    }

}

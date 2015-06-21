package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class BraveTracerTest {

    private static final String SERVICE_NAME = "service";
    private static final int PORT = 80;
    private static final String IP = "10.0.1.5";
    private SpanCollector mockSpanCollector;
    private TraceFilter mockTraceFilter;
    private BraveTracer braveTracer;
    ClientTracer clientTracer;
    ServerTracer serverTracer;
    
    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        mockTraceFilter = mock(TraceFilter.class);
        clientTracer = Brave.getClientTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        serverTracer = Brave.getServerTracer(mockSpanCollector, Arrays.asList(mockTraceFilter));
        final EndpointSubmitter endpointSubmitter = Brave.getEndpointSubmitter();
        endpointSubmitter.submit(IP, PORT, SERVICE_NAME);
        
        braveTracer = new BraveTracer(clientTracer, serverTracer, endpointSubmitter);
    }

    @Test
    public void testBraveTracer() {
    	final ClientTracer clientTracer = braveTracer.clientTracer;
        assertNotNull(clientTracer);
        assertTrue(this.clientTracer == clientTracer);
        assertTrue("We expect instance of ClientTracer", clientTracer instanceof ClientTracer);
        
    	final ServerTracer serverTracer = braveTracer.serverTracer;
        assertNotNull(serverTracer);
        assertTrue(this.serverTracer == serverTracer);
        assertTrue("We expect instance of ServerTracer", serverTracer instanceof ServerTracer);
    }
}

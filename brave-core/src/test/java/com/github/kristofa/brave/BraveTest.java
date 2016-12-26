package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.reporter.Reporter;

public class BraveTest {

    private Reporter<Span> fakeReporter = span -> {
    };
    private Sampler mockSampler;
    private Brave brave;

    @Before
    public void setup() {
        mockSampler = mock(Sampler.class);
        // -1062731775 = 192.168.0.1
        final Brave.Builder builder = new Brave.Builder(-1062731775, 8080, "unknown");
        brave = builder.reporter(fakeReporter).traceSampler(mockSampler).build();
    }

    @Test
    public void testGetClientTracer() {
        final ClientTracer clientTracer = brave.clientTracer();
        assertNotNull(clientTracer);
        assertSame("ClientTracer should be configured with the reporter we submitted.", fakeReporter,
            clientTracer.reporter());
        assertSame("ClientTracer should be configured with the traceSampler we submitted.",
            mockSampler, clientTracer.spanIdFactory().sampler());

        final ClientTracer secondClientTracer =
            brave.clientTracer();
        assertSame("It is important that each client tracer we get shares same state.",
                   clientTracer.spanAndEndpoint().state(), secondClientTracer.spanAndEndpoint().state());
    }

    @Test
    public void testGetServerTracer() {
        final ServerTracer serverTracer = brave.serverTracer();
        assertNotNull(serverTracer);
        assertSame(fakeReporter, serverTracer.reporter());
        assertSame("ServerTracer should be configured with the traceSampler we submitted.",
            mockSampler, serverTracer.spanIdFactory().sampler());

        final ServerTracer secondServerTracer =
            brave.serverTracer();
        assertSame("It is important that each server tracer we get shares same state.",
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
    public void testGetLocalTracer() {
        final LocalTracer localTracer = brave.localTracer();
        assertNotNull(localTracer);
        assertSame(fakeReporter, localTracer.reporter());
        assertSame("LocalTracer should be configured with the traceSampler we submitted.",
                mockSampler, localTracer.spanIdFactory().sampler());

        final LocalTracer secondLocalTracer =
                brave.localTracer();
        assertSame("It is important that each local tracer we get shares same state.",
                localTracer.spanAndEndpoint().state(), secondLocalTracer.spanAndEndpoint().state());
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

package com.github.kristofa.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.zipkin.gen.Endpoint;
import zipkin.reporter.Reporter;

public class LocalTracingInheritenceTest {

    private Reporter<zipkin.Span> reporter;
    private Sampler sampler;
    private Brave brave;
    private ServerClientAndLocalSpanState state;
    private ThreadFactory threadFactory;

    @Before
    public void setup() throws UnknownHostException {
        reporter = mock(Reporter.class);
        sampler = Sampler.ALWAYS_SAMPLE;

        final int ip = InetAddressUtilities.toInt(InetAddressUtilities.getLocalHostLANAddress());
        final String serviceName = LocalTracingInheritenceTest.class.getSimpleName();
        state = new InheritableServerClientAndLocalSpanState(Endpoint.create(serviceName, ip));
        brave = new Brave.Builder(state)
                .reporter(reporter)
                .traceSampler(sampler)
                .build();

        threadFactory = new ThreadFactoryBuilder().setNameFormat("brave-%d").build();

        checkState();
    }

    @After
    public void tearDown() throws Exception {
        checkState();
    }

    private void checkState() {
        LocalTracer localTracer = brave.localTracer();
        ServerClientAndLocalSpanState state = localTracer.spanAndEndpoint().state();
        assertThat(state.getCurrentServerSpan()).isSameAs(ServerSpan.EMPTY);
        assertThat(state.getCurrentClientSpan()).isNull();
        assertThat(state.getCurrentLocalSpan()).isNull();
        assertThat(localTracer.spanAndEndpoint().span()).isNull();
    }

    @Test
    public void testGetClientTracer() {
        final ClientTracer clientTracer = brave.clientTracer();
        assertNotNull(clientTracer);
        assertSame("ClientTracer should be configured with the spanreportor we submitted.", reporter,
                clientTracer.reporter());
        assertSame("ClientTracer should be configured with the traceSampler we submitted.",
                sampler, clientTracer.spanIdFactory().sampler());

        final ClientTracer secondClientTracer = brave.clientTracer();
        assertSame("It is important that each client tracer we get shares same state.",
                clientTracer.spanAndEndpoint().state(), secondClientTracer.spanAndEndpoint().state());
    }

    @Test
    public void testGetServerTracer() {
        final ServerTracer serverTracer = brave.serverTracer();
        assertNotNull(serverTracer);
        assertSame(reporter, serverTracer.reporter());
        assertSame("ServerTracer should be configured with the traceSampler we submitted.",
                sampler, serverTracer.spanIdFactory().sampler());

        final ServerTracer secondServerTracer = brave.serverTracer();
        assertSame("It is important that each client tracer we get shares same state.",
                serverTracer.spanAndEndpoint().state(), secondServerTracer.spanAndEndpoint().state());
    }

    @Test
    public void testGetLocalTracer() {
        final LocalTracer localTracer = brave.localTracer();
        assertNotNull(localTracer);
        assertSame(reporter, localTracer.reporter());
        assertSame("LocalTracer should be configured with the traceSampler we submitted.",
                sampler, localTracer.spanIdFactory().sampler());

        final LocalTracer secondLocalTracer = brave.localTracer();
        assertSame("It is important that each local tracer we get shares same state.",
                localTracer.spanAndEndpoint().state(), secondLocalTracer.spanAndEndpoint().state());
    }

    @Test
    public void testStateBetweenServerAndClient() {
        final ClientTracer clientTracer = brave.clientTracer();
        final ServerTracer serverTracer = brave.serverTracer();
        final LocalTracer localTracer = brave.localTracer();

        assertSame("Client and server tracers should share same state.", clientTracer.spanAndEndpoint().state(),
                serverTracer.spanAndEndpoint().state());

        assertSame("Client and local tracers should share same state.", clientTracer.spanAndEndpoint().state(),
                localTracer.spanAndEndpoint().state());

        assertSame("Server and local tracers should share same state.", serverTracer.spanAndEndpoint().state(),
                localTracer.spanAndEndpoint().state());
    }

    @Test
    public void testNestedLocalTraces() throws Exception {
        LocalTracer localTracer = brave.localTracer();

        SpanId span1 = localTracer.startNewSpan("comp1", "op1");
        try {
            SpanId span2 = localTracer.startNewSpan("comp2", "op2");
            try {
                SpanId span3 = localTracer.startNewSpan("comp3", "op3");
                try {
                    SpanId span4 = localTracer.startNewSpan("comp4", "op4");
                    try {
                        assertThat(state.getCurrentLocalSpan().getId()).isEqualTo(span4.spanId);
                    } finally {

                        localTracer.finishSpan();
                    }

                    assertThat(state.getCurrentLocalSpan().getId()).isEqualTo(span3.spanId);
                } finally {
                    localTracer.finishSpan();
                }

                assertThat(state.getCurrentLocalSpan().getId()).isEqualTo(span2.spanId);
            } finally {
                localTracer.finishSpan();
            }

            assertThat(state.getCurrentLocalSpan().getId()).isEqualTo(span1.spanId);
        } finally {
            localTracer.finishSpan();
        }

        assertThat(state.getCurrentLocalSpan()).isNull();
        verify(reporter, times(4)).report(any(zipkin.Span.class));
        localTracer.finishSpan(); // unmatched finish should no-op
        verifyNoMoreInteractions(reporter);
    }

    @Test
    public void testManyNestedLocalTraces() throws Exception {
        brave = new Brave.Builder(state)
                .reporter(reporter)
                .traceSampler(Sampler.ALWAYS_SAMPLE)
                .build();

        LocalTracer localTracer = brave.localTracer();

        assertThat(state.getCurrentLocalSpan()).isNull();

        int limit = 128;
        for (int i = 1; i <= limit; i <<= 1) {
            runLocalSpan(i, limit);
        }

        assertThat(state.getCurrentLocalSpan()).isNull();
        verify(reporter, times(777)).report(any(zipkin.Span.class));
        localTracer.finishSpan(); // unmatched finish should no-op
        verifyNoMoreInteractions(reporter);
    }

    private void runLocalSpan(final int iteration, final int limit) {
        LocalTracer localTracer = brave.localTracer();
        SpanId spanId = localTracer.startNewSpan("comp" + iteration, "op" + iteration);
        try {
            if (iteration < limit) {
                runLocalSpan(iteration + 1, limit);
            }
            assertThat(state.getCurrentLocalSpan().getId()).isEqualTo(spanId.spanId);
        } finally {
            localTracer.finishSpan();
        }
    }

    @Test
    public void testNestedThreads() throws Exception {
        LocalTracer localTracer = brave.localTracer();

        for (int i = 0; i < 4; i++) {
            int threadId = 0;
            SpanId span0 = localTracer.startNewSpan("thread-" + threadId, "run");
            assertThat(span0).isNotNull();
            assertThat(span0.root()).isTrue();
            assertThat(span0.spanId).isEqualTo(span0.traceId);
            assertThat(span0.spanId).isEqualTo(span0.parentId);
            assertThat(span0.nullableParentId()).isNull();

            try {
                runThreads(16, 4);
            } finally {
                localTracer.finishSpan();
            }

            localTracer.finishSpan(); // unmatched finish should no-op
        }

        assertThat(state.getCurrentLocalSpan()).isNull();
        verify(reporter, times(844)).report(any(zipkin.Span.class));
        localTracer.finishSpan(); // unmatched finish should no-op
        verifyNoMoreInteractions(reporter);
    }

    private void runThreads(int breadth, int depth) throws InterruptedException {
        List<Thread> threads = new ArrayList<Thread>(Math.abs(breadth * depth));
        for (int i = 1; i < breadth; i++) {
            for (int j = 0; j < depth; j++) {
                threads.add(threadFactory.newThread(createRunnable(i, j)));
            }
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    private Runnable createRunnable(final int breadth, final int depth) {
        final SpanId baseSpan = brave.localTracer().startNewSpan("thread-" + breadth, "create-" + breadth + ":" + depth);
        assertThat(baseSpan).isNotNull();
        assertThat(baseSpan.nullableParentId()).isNotNull();
        assertThat(baseSpan.root()).isFalse();
        assertThat(baseSpan.spanId).isNotEqualTo(baseSpan.traceId);
        try {
            return () -> {
                String originalThreadName = Thread.currentThread().getName();
                Thread.currentThread().setName(originalThreadName + "]"
                        + "[create-" + breadth + ":" + depth + "]");
                LocalTracer localTracer = brave.localTracer();
                SpanId runnableSpan = localTracer.startNewSpan("thread-" + breadth + ":" + depth,
                        "run-" + breadth + ":" + depth);
                assertThat(runnableSpan).isNotNull();
                assertThat(runnableSpan.nullableParentId()).isNotNull();
                assertThat(runnableSpan.root()).isFalse();
                assertThat(runnableSpan.spanId).isNotEqualTo(runnableSpan.traceId);

                try {
                    runThreads(2, depth - 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    localTracer.finishSpan();
                    Thread.currentThread().setName(originalThreadName);
                }
            };
        } finally {
            brave.localTracer().finishSpan();
        }
    }
}

package com.github.kristofa.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertSame;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.zipkin.gen.Endpoint;
import zipkin.reporter.Reporter;

public class LocalTracingInheritanceTest {

    private Reporter<zipkin.Span> reporter;
    private ConcurrentLinkedDeque<String> spansReported = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<String> spansCreated = new ConcurrentLinkedDeque<>();
    private Brave brave;
    private ServerClientAndLocalSpanState state;
    private ThreadFactory threadFactory;

    @Before
    public void setup() throws UnknownHostException {
        reporter = s -> spansReported.add(s.name);

        final int ip = InetAddressUtilities.toInt(InetAddressUtilities.getLocalHostLANAddress());
        final String serviceName = LocalTracingInheritanceTest.class.getSimpleName();
        state = new InheritableServerClientAndLocalSpanState(Endpoint.create(serviceName, ip));
        brave = new Brave.Builder(state).reporter(reporter).build();
        threadFactory = new ThreadFactoryBuilder().setNameFormat("brave-%d").build();

        checkState();
    }

    @After
    public void tearDown() throws Exception {
        checkState();
    }

    private void checkState() {
        LocalTracer localTracer = brave.localTracer();
        assertThat(localTracer.currentServerSpan().getCurrentServerSpan())
            .isSameAs(ServerSpan.EMPTY);
        assertThat(localTracer.currentSpan().get()).isNull();
    }

    @Test
    public void testStateBetweenServerAndClient() {
        final ClientTracer clientTracer = brave.clientTracer();
        final ServerTracer serverTracer = brave.serverTracer();
        final LocalTracer localTracer = brave.localTracer();

        assertSame("Client and server tracers should share same state.", clientTracer.currentServerSpan(),
                serverTracer.currentSpan());

        assertSame("Client and local tracers should share same state.", clientTracer.currentLocalSpan(),
                localTracer.currentSpan());

        assertSame("Server and local tracers should share same state.", serverTracer.currentSpan(),
                localTracer.currentServerSpan());
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
        assertThat(spansReported).hasSize(4);
        localTracer.finishSpan(); // unmatched finish should no-op
        assertThat(spansReported).hasSize(4);
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
        assertThat(spansReported).hasSize(777);
        localTracer.finishSpan(); // unmatched finish should no-op
        assertThat(spansReported).hasSize(777);
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
            String span0Name = "run-" + i;
            SpanId span0 = localTracer.startNewSpan("thread-" + i, span0Name);
            spansCreated.add(span0Name);
            assertThat(span0).isNotNull();
            assertThat(span0.root()).isTrue();
            assertThat(span0.traceId).isNotZero();
            assertThat(span0.spanId).isNotZero();
            assertThat(span0.nullableParentId()).isNull();

            try {
                runThreads(16, 4);
            } finally {
                localTracer.finishSpan();
            }

            localTracer.finishSpan(); // unmatched finish should no-op
        }

        assertThat(state.getCurrentLocalSpan()).isNull();
        assertThat(spansReported).containsOnlyElementsOf(spansCreated);
        localTracer.finishSpan(); // unmatched finish should no-op
        assertThat(spansReported).containsOnlyElementsOf(spansCreated);
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
        String baseName = "create-" + breadth + ":" + depth;
        final SpanId baseSpan = brave.localTracer().startNewSpan("thread-" + breadth, baseName);
        spansCreated.add(baseName);

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
                String runnableName = "run-" + breadth + ":" + depth;
                SpanId runnableSpan = localTracer.startNewSpan("thread-" + breadth + ":" + depth,
                    runnableName);
                spansCreated.add(runnableName);
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

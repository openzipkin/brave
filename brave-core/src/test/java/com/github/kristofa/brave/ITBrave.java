package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.Test;

import com.twitter.zipkin.gen.Span;

/**
 * Integration isSampled for Brave api. Tests submitting server/client spans in parallel threads. Each thread represents a
 * different trace. So this simulates having multiple parallel requests
 * 
 * @author kristof
 */
public class ITBrave {

    private final static int NUMBER_PARALLEL_THREADS = 4;
    private final static int NUMBER_OF_REQUESTS = 1000;

    @Test
    public void testServerClientAndLocalSpanCycle() throws InterruptedException, ExecutionException {
        final ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(NUMBER_PARALLEL_THREADS);
        try {
            final Collection<Future<Integer>> futures = new ArrayList<Future<Integer>>();

            for (int index = 1; index <= NUMBER_OF_REQUESTS; index++) {
                futures.add(newFixedThreadPool.submit(new SpanThread()));
            }

            for (final Future<Integer> future : futures) {
                assertEquals(Integer.valueOf(2), future.get());
            }

        } finally {
            newFixedThreadPool.shutdown();
        }

    }

    class SpanThread implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {

            final IntegrationTestSpanCollector mockSpanCollector = new IntegrationTestSpanCollector();
            final Brave.Builder builder = new Brave.Builder("serviceName");
            final Brave brave = builder.spanCollector(mockSpanCollector).build();


            final ServerTracer serverTracer = brave.serverTracer();

            final Random random = new Random();

            final String serverSpanName = "server span name " + random.nextLong();
            serverTracer.setStateCurrentTrace(random.nextLong(), random.nextLong(), null, serverSpanName);

            serverTracer.setServerReceived();

            serverTracer.submitAnnotation("custom annotation");

            // Simulate client.
            final ClientTracer clientTracer = brave.clientTracer();
            final String clientSpanName = "client span name " + random.nextLong();
            clientTracer.startNewSpan(clientSpanName);
            clientTracer.setClientSent();
            clientTracer.setClientReceived();

            // Simulate local.
            final LocalTracer localTracer = brave.localTracer();
            final String localSpanName = "local span name " + random.nextLong();
            localTracer.startNewSpan("test", localSpanName);
            localTracer.finishSpan();

            serverTracer.setServerSend();

            final List<Span> collectedSpans = mockSpanCollector.getCollectedSpans();
            assertEquals("Expected 3 collected spans.", 3, collectedSpans.size());
            final Span clientSpan = collectedSpans.get(0);
            final Span localSpan = collectedSpans.get(1);
            final Span serverSpan = collectedSpans.get(2);

            assertTrue(serverSpan.trace_id != 0);
            assertFalse(serverSpan.isSetParent_id());
            assertTrue(serverSpan.id != 0);
            assertEquals(serverSpanName, serverSpan.name);

            assertEquals(serverSpan.trace_id, clientSpan.trace_id);
            assertEquals(serverSpan.id, clientSpan.parent_id);
            assertTrue(clientSpan.id != 0);
            assertEquals(clientSpanName, clientSpan.name);

            assertEquals(serverSpan.trace_id, localSpan.trace_id);
            assertEquals(serverSpan.id, localSpan.parent_id);
            assertTrue(localSpan.id != 0);
            assertEquals(localSpanName, localSpan.name);

            assertEquals("Span ids should be different.", 3, Stream.of(serverSpan.id, clientSpan.id, localSpan.id).distinct().count());
            assertEquals("Expect sr, ss and 1 custom annotation.", 3, serverSpan.getAnnotations().size());
            assertEquals(2, clientSpan.getAnnotations().size());
            assertFalse(localSpan.isSetAnnotations());

            return 2;

        }
    }

    class IntegrationTestSpanCollector implements SpanCollector {

        private final List<Span> collectedSpans = new ArrayList<Span>();

        @Override
        public void collect(final Span span) {
            collectedSpans.add(span);
        }

        public List<Span> getCollectedSpans() {
            return collectedSpans;
        }

        @Override
        public void addDefaultAnnotation(final String name, final String value) {
            throw new UnsupportedOperationException();
        }

    }

}

package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import com.twitter.zipkin.gen.Span;

/**
 * Integration test for Brave api. Tests submitting server/client spans in parallel threads. Each thread represents a
 * different trace. So this simulates having multiple parallel requests
 * 
 * @author kristof
 */
public class ITBrave {

    private final static int NUMBER_PARALLEL_THREADS = 4;
    private final static int NUMBER_OF_REQUESTS = 1000;

    @Test
    public void testServerAndClientSpanCycle() throws InterruptedException, ExecutionException {
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

            final EndPointSubmitter endPointSubmitter = Brave.getEndPointSubmitter();
            if (!endPointSubmitter.endPointSubmitted()) {
                endPointSubmitter.submit("10.0.1.6", 80, "serviceName");
            }

            final IntegrationTestSpanCollector mockSpanCollector = new IntegrationTestSpanCollector();
            final ServerTracer serverTracer = Brave.getServerTracer(mockSpanCollector);

            final Random random = new Random();

            final String serverSpanName = "server span name " + random.nextLong();
            serverTracer.setSpan(random.nextLong(), random.nextLong(), random.nextLong(), serverSpanName);
            serverTracer.setSample(true);

            serverTracer.setServerReceived();

            final long startDate = System.currentTimeMillis();
            serverTracer.submitAnnotation("custom annotation", startDate, startDate + 1000);

            // Simulate client.
            final ClientTracer clientTracer =
                Brave.getClientTracer(mockSpanCollector, Arrays.asList(Brave.getTraceAllTraceFilter()));
            final String clientSpanName = "client span name " + random.nextLong();
            clientTracer.startNewSpan(clientSpanName);
            clientTracer.setClientSent();
            clientTracer.setClientReceived();

            serverTracer.setServerSend();

            final List<Span> collectedSpans = mockSpanCollector.getCollectedSpans();
            assertEquals("Expected 2 collected spans.", 2, collectedSpans.size());
            final Span clientSpan = collectedSpans.get(0);
            final Span serverSpan = collectedSpans.get(1);

            assertTrue(serverSpan.getTrace_id() != 0);
            assertTrue(serverSpan.getId() != 0);
            assertTrue(serverSpan.getParent_id() != 0);

            assertTrue(clientSpan.getTrace_id() != 0);
            assertTrue(clientSpan.getId() != 0);
            assertTrue(clientSpan.getParent_id() != 0);

            assertEquals("Should belong to same trace.", serverSpan.getTrace_id(), clientSpan.getTrace_id());
            assertTrue("Span ids should be different.", serverSpan.getId() != clientSpan.getId());
            assertEquals("Parent span id of client span should be equal to server span id.", serverSpan.getId(),
                clientSpan.getParent_id());

            assertEquals("Expect sr, ss and 1 custom annotation.", 3, serverSpan.getAnnotations().size());
            assertEquals(2, clientSpan.getAnnotations().size());

            return 2;

        }
    }

    class IntegrationTestSpanCollector implements SpanCollector {

        private final List<Span> collectedSpans = new ArrayList<Span>();
        private int closeCalled = 0;

        @Override
        public void collect(final Span span) {
            collectedSpans.add(span);
        }

        public List<Span> getCollectedSpans() {
            return collectedSpans;
        }

        @Override
        public void close() {
            closeCalled++;
        }

        public int howManyTimesCloseCalled() {
            return closeCalled;
        }

    }

}

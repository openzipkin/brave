package com.github.kristofa.brave.scribe;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.InternalSpan;
import com.twitter.zipkin.gen.Span;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScribeSpanCollectorMetricsTest {
    static {
        InternalSpan.initializeInstanceForTests();
    }

    private static final String HOST = "localhost";
    private static final int PORT = FreePortProvider.getNewFreePort();
    Span span = InternalSpan.instance.newSpan(SpanId.builder().traceId(1).spanId(2).build());

    private static ScribeServer scribeServer;
    private EventsHandler eventsHandler;

    private static class EventsHandler implements SpanCollectorMetricsHandler {

        public int acceptedSpans = 0;
        public int droppedSpans = 0;

        @Override
        public synchronized void incrementAcceptedSpans(int quantity) {
            acceptedSpans += quantity;
        }

        @Override
        public synchronized void incrementDroppedSpans(int quantity) {
            droppedSpans += quantity;
        }
    }


    @BeforeClass
    public static void beforeClass() throws TTransportException {
        scribeServer = new ScribeServer(PORT);
        scribeServer.start();
    }

    @AfterClass
    public static void afterClass() {
        scribeServer.stop();
    }

    @Before
    public void setup() {
        eventsHandler = new EventsHandler();
        scribeServer.clearReceivedSpans();
        scribeServer.clearDelay();
    }

    @Test
    public void testCollectorMetricsWhenSpansDeliveredSuccessfully() {
        // given
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector(HOST, PORT, params)) {
            scribeSpanCollector.collect(span);
        }

        // then
        assertEquals(1, eventsHandler.acceptedSpans);
        assertEquals(0, eventsHandler.droppedSpans);
    }

    @Test
    public void testCollectorMetricsWhenSpansDroppedDueToFullQueue() throws InterruptedException {
        // given
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);
        params.setQueueSize(1);
        params.setBatchSize(1);
        scribeServer.introduceDelay(500, TimeUnit.MILLISECONDS);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector(HOST, PORT, params)) {
            scribeSpanCollector.collect(span);
            scribeSpanCollector.collect(span);
            scribeSpanCollector.collect(span);
        }

        // then
        assertEquals(3, eventsHandler.acceptedSpans);
        assertTrue(eventsHandler.droppedSpans > 0);
        assertEquals(scribeServer.getReceivedSpans().size(),
                eventsHandler.acceptedSpans - eventsHandler.droppedSpans);
    }

    @Test
    public void testCollectorMetricsWhenSpansDroppedDueToConnectionFailure() {
        // given
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);
        params.setFailOnSetup(false);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("invalid-host", PORT, params)) {
            scribeSpanCollector.collect(span);
        }

        // then
        assertEquals(1, eventsHandler.acceptedSpans);
        assertEquals(1, eventsHandler.droppedSpans);
    }

}

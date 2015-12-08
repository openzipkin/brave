package com.github.kristofa.brave.scribe;

import com.codahale.metrics.MetricRegistry;
import com.twitter.zipkin.gen.Span;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.github.kristofa.brave.scribe.DropwizardMetricsScribeCollectorMetricsHandlerExample.ACCEPTED_METER;
import static com.github.kristofa.brave.scribe.DropwizardMetricsScribeCollectorMetricsHandlerExample.DROPPED_METER;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScribeSpanCollectorMetricsTest {

    private static final String HOST = "localhost";
    private static final int PORT = FreePortProvider.getNewFreePort();
    private static final Span SPAN = new Span(1, "span name", 2, emptyList(), emptyList());

    private static ScribeServer scribeServer;

    private MetricRegistry metricRegistry = new MetricRegistry();

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
        scribeServer.clearReceivedSpans();
        scribeServer.clearDelay();
    }

    @Test
    public void testCollectorMetricsWhenSpansDeliveredSuccessfully() {
        // given
        DropwizardMetricsScribeCollectorMetricsHandlerExample eventsHandler = new DropwizardMetricsScribeCollectorMetricsHandlerExample(metricRegistry);
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector(HOST, PORT, params)) {
            scribeSpanCollector.collect(SPAN);
        }

        // then
        assertEquals(1, metricRegistry.meter(ACCEPTED_METER).getCount());
        assertEquals(0, metricRegistry.meter(DROPPED_METER).getCount());
    }

    @Test
    public void testCollectorMetricsWhenSpansDroppedDueToFullQueue() throws InterruptedException {
        // given
        final DropwizardMetricsScribeCollectorMetricsHandlerExample eventsHandler = new DropwizardMetricsScribeCollectorMetricsHandlerExample(metricRegistry);
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);
        params.setQueueSize(1);
        params.setBatchSize(1);
        scribeServer.introduceDelay(500, TimeUnit.MILLISECONDS);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector(HOST, PORT, params)) {
            scribeSpanCollector.collect(SPAN);
            scribeSpanCollector.collect(SPAN);
            scribeSpanCollector.collect(SPAN);
        }

        // then
        assertEquals(3, metricRegistry.meter(ACCEPTED_METER).getCount());
        assertTrue(metricRegistry.meter(DROPPED_METER).getCount() > 0);
        assertEquals(scribeServer.getReceivedSpans().size(),
                metricRegistry.meter(ACCEPTED_METER).getCount() - metricRegistry.meter(DROPPED_METER).getCount());
    }

    @Test
    public void testCollectorMetricsWhenSpansDroppedDueToConnectionFailure() {
        // given
        final DropwizardMetricsScribeCollectorMetricsHandlerExample eventsHandler = new DropwizardMetricsScribeCollectorMetricsHandlerExample(metricRegistry);
        ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setMetricsHandler(eventsHandler);
        params.setFailOnSetup(false);

        // when
        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("invalid-host", PORT, params)) {
            scribeSpanCollector.collect(SPAN);
        }

        // then
        assertEquals(1, metricRegistry.meter(ACCEPTED_METER).getCount());
        assertEquals(1, metricRegistry.meter(DROPPED_METER).getCount());
    }

}

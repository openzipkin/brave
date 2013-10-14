package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.Span;

/**
 * Integration test for {@link ZipkinSpanCollector} that stress tests the {@link ZipkinSpanCollector}.
 * 
 * @author kristof
 */
public class ITZipkinSpanCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ITZipkinSpanCollector.class);
    private static final int QUEUE_SIZE = 5;
    private static final int FIRST_BURST_OF_SPANS = 100;
    private static final int SECOND_BURST_OF_SPANS = 20;

    private static final int PORT = 9110;
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;
    private static final String SPAN_NAME = "SpanName";

    private static ZipkinCollectorServer zipkinCollectorServer;

    @BeforeClass
    public static void setupBeforeClass() throws TTransportException {
        zipkinCollectorServer = new ZipkinCollectorServer(PORT);
        zipkinCollectorServer.start();

    }

    @AfterClass
    public static void tearDownAfterClass() {
        zipkinCollectorServer.stop();
    }

    @Before
    public void setup() {
        zipkinCollectorServer.clearReceivedSpans();
    }

    /**
     * The test will submit a first burst of spans (configured to 100) in a for loop without delay while only having a small
     * queue size (configured to 5). After those 100 the test will sleep for 8 seconds which is longer than the timeout time
     * for the blocking queue. Next it will submit another 20 spans in a for loop without delay, wait for 5 seconds and shut
     * down the collector. At this point we expect to have received all 120 spans.
     * <p/>
     * So implicitly this test tests:
     * <ol>
     * <li>a full queue at which client will be blocked.</li>
     * <li>a wait time of longer than 5 seconds which means SpanProcessingThread will run into timeout.
     * </ol>
     * 
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testStressTestAndCauseSpanProcessingThreadTimeOut() throws TTransportException, InterruptedException {

        final ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
        params.setQueueSize(100);
        params.setBatchSize(50);

        final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT, params);
        try {

            final Span span = new Span();
            span.setId(SPAN_ID);
            span.setTrace_id(TRACE_ID);
            span.setName(SPAN_NAME);

            for (int i = 1; i <= FIRST_BURST_OF_SPANS; i++) {
                LOGGER.info("Submitting Span nr " + i + "/" + FIRST_BURST_OF_SPANS);
                zipkinSpanCollector.collect(span);
            }
            LOGGER.info("Sleep 8 seconds");
            Thread.sleep(8000);
            for (int i = 1; i <= SECOND_BURST_OF_SPANS; i++) {
                LOGGER.info("Submitting Span nr " + i + "/" + SECOND_BURST_OF_SPANS);
                zipkinSpanCollector.collect(span);
            }
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        } finally {
            zipkinSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = zipkinCollectorServer.getReceivedSpans();
        assertEquals(120, serverCollectedSpans.size());
    }

    /**
     * The test will submit a burst of 110 spans in a for loop without delay while only having a small queue size (configured
     * to 5). But the server is configured to take a long time to consume span, longer than the 5 seconds. So implicitly this
     * tests:
     * <ol>
     * <li>a full queue at which client will be blocked for more then its time out value. This means spans are lost, do not
     * end up in collector server.</li>
     * <li>it also shows that stopping ZipkinSpanCollector before all queued spans have been submitted works.</li>
     * </ol>
     * 
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testOfferTimeOut() throws TTransportException, InterruptedException {

        final int hunderdTen = 110;

        final ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
        params.setQueueSize(QUEUE_SIZE);
        params.setBatchSize(50);

        final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT, params);
        try {

            final Span span = new Span();
            span.setId(SPAN_ID);
            span.setTrace_id(TRACE_ID);
            span.setName(SPAN_NAME);

            for (int i = 1; i <= hunderdTen; i++) {
                LOGGER.info("Submitting Span nr " + i + "/" + hunderdTen);
                zipkinSpanCollector.collect(span);
            }
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        } finally {
            zipkinSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = zipkinCollectorServer.getReceivedSpans();
        assertTrue(serverCollectedSpans.size() < hunderdTen);

    }

}

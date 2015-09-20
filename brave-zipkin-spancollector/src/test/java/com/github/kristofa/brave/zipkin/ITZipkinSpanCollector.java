package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

import org.apache.thrift.transport.TTransportException;
import org.junit.*;

import com.twitter.zipkin.gen.Span;

/**
 * Integration test for {@link ZipkinSpanCollector} that stress tests the {@link ZipkinSpanCollector}.
 * 
 * @author kristof
 */
public class ITZipkinSpanCollector {

    private static final Logger LOGGER = Logger.getLogger(ITZipkinSpanCollector.class.getName());

    private static final int PORT = FreePortProvider.getNewFreePort();
    private static final String SPAN_NAME = "SpanName";

    private static ZipkinCollectorServer zipkinCollectorServer;

    private long traceId = 1;

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
        traceId = 1;
        zipkinCollectorServer.clearReceivedSpans();
    }

    /**
     * The test will submit a first burst of spans (configured to 100) in a for loop without delay.
     * After those 100 the test will sleep for 8 seconds. Next it will submit another 20 spans in a for
     * loop without delay, wait for 5 seconds and shut down the collector. At this point we expect to have received all 120 spans.
     *
     * So implicitly this test tests:
     * <ul>
     * <li>a wait time of longer than 5 seconds which means SpanProcessingThread will run into timeout.</li>
     * <li>test that on shut down the remaining collected spans are submitted</li>
     * </ul>
     *
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testStressTestAndCauseSpanProcessingThreadTimeOut() throws TTransportException, InterruptedException {
        final int firstBurstOfSpans = 100;
        final int secondBurstOfSpans = 20;

        final ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
        params.setQueueSize(100);
        params.setBatchSize(50);

        long traceId = 1;
        try (ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT, params)) {

            submitSpans(zipkinSpanCollector, firstBurstOfSpans);
            LOGGER.info("Sleep 8 seconds");
            Thread.sleep(8000);
            submitSpans(zipkinSpanCollector, secondBurstOfSpans);
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        }
        assertEquals(firstBurstOfSpans + secondBurstOfSpans, zipkinCollectorServer.getReceivedSpans().size());
    }


    private void submitSpans(ZipkinSpanCollector zipkinSpanCollector, int nrOfSpans) {
        for (int i = 1; i <= nrOfSpans; i++) {
            LOGGER.info("Submitting Span nr " + i + "/" + nrOfSpans);
            final Span span = span(traceId);
            traceId++;
            zipkinSpanCollector.collect(span);
        }
    }

    private Span span(long traceId) {
        final Span span = new Span();
        span.setId(traceId);
        span.setTrace_id(traceId);
        span.setName(SPAN_NAME);
        return span;
    }

}

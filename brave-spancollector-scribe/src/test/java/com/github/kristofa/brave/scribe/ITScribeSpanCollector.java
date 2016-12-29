package com.github.kristofa.brave.scribe;

import static org.junit.Assert.assertEquals;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.InternalSpan;
import java.util.logging.Logger;

import org.apache.thrift.transport.TTransportException;
import org.junit.*;

import com.twitter.zipkin.gen.Span;

/**
 * Integration isSampled for {@link ScribeSpanCollector} that stress tests the {@link ScribeSpanCollector}.
 * 
 * @author kristof
 */
public class ITScribeSpanCollector {
    static {
        InternalSpan.initializeInstanceForTests();
    }

    private static final Logger LOGGER = Logger.getLogger(ITScribeSpanCollector.class.getName());

    private static final int PORT = FreePortProvider.getNewFreePort();

    private static ScribeServer scribeServer;

    private long traceId = 1;

    @BeforeClass
    public static void setupBeforeClass() throws TTransportException {
        scribeServer = new ScribeServer(PORT);
        scribeServer.start();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        scribeServer.stop();
    }

    @Before
    public void setup() {
        traceId = 1;
        scribeServer.clearReceivedSpans();
    }

    /**
     * The isSampled will submit a first burst of spans (configured to 100) in a for loop without delay.
     * After those 100 the isSampled will sleep for 8 seconds. Next it will submit another 20 spans in a for
     * loop without delay, wait for 5 seconds and shut down the collector. At this point we expect to have received all 120 spans.
     *
     * So implicitly this isSampled tests:
     * <ul>
     * <li>a wait time of longer than 5 seconds which means SpanProcessingThread will run into timeout.</li>
     * <li>isSampled that on shut down the remaining collected spans are submitted</li>
     * </ul>
     *
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testStressTestAndCauseSpanProcessingThreadTimeOut() throws TTransportException, InterruptedException {
        final int firstBurstOfSpans = 100;
        final int secondBurstOfSpans = 20;

        final ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setQueueSize(100);
        params.setBatchSize(50);

        try (ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT, params)) {

            submitSpans(scribeSpanCollector, firstBurstOfSpans);
            LOGGER.info("Sleep 8 seconds");
            Thread.sleep(8000);
            submitSpans(scribeSpanCollector, secondBurstOfSpans);
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        }
        assertEquals(firstBurstOfSpans + secondBurstOfSpans, scribeServer.getReceivedSpans().size());
    }


    private void submitSpans(ScribeSpanCollector scribeSpanCollector, int nrOfSpans) {
        for (int i = 1; i <= nrOfSpans; i++) {
            LOGGER.info("Submitting Span nr " + i + "/" + nrOfSpans);
            final Span span = span(traceId);
            traceId++;
            scribeSpanCollector.collect(span);
        }
    }

    static Span span(long traceId) {
        return InternalSpan.instance.newSpan(SpanId.builder().spanId(traceId).build());
    }
}

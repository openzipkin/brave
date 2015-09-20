package com.github.kristofa.brave.zipkin;

import com.twitter.zipkin.gen.Span;
import org.apache.thrift.transport.TTransportException;
import org.junit.*;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;


public class ITZipkinSpanCollectorOfferTimeout {

    private static final Logger LOGGER = Logger.getLogger(ITZipkinSpanCollector.class.getName());
    private static final int QUEUE_SIZE = 5;

    private static final int PORT = FreePortProvider.getNewFreePort();
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

        final int hundredTen = 110;

        final ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
        params.setQueueSize(QUEUE_SIZE);
        params.setBatchSize(50);

        final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT, params);
        try {

            final Span span = new Span();
            span.setId(SPAN_ID);
            span.setTrace_id(TRACE_ID);
            span.setName(SPAN_NAME);

            for (int i = 1; i <= hundredTen; i++) {
                LOGGER.info("Submitting Span nr " + i + "/" + hundredTen);
                zipkinSpanCollector.collect(span);
            }
            LOGGER.info("Sleep 5 seconds");
            Thread.sleep(5000);
        } finally {
            zipkinSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = zipkinCollectorServer.getReceivedSpans();
        assertTrue(serverCollectedSpans.size() < hundredTen);

    }
}

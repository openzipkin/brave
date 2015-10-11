package com.github.kristofa.brave.scribe;

import static org.junit.Assert.assertEquals;

import org.apache.thrift.transport.TTransportException;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ITScribeSpanCollectorFailOnSetup {

    private static final int PORT = FreePortProvider.getNewFreePort();
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;
    private static final String SPAN_NAME = "SpanName";

    /**
     * Integration test that checks failOnSetup = false. The test basically shows that no exception is thrown when the server
     * is down when initializing but we are still able to reconnect in case the server gets up at a later stage.
     * 
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testFailOnSetupFalse() throws TTransportException, InterruptedException {
        final ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setFailOnSetup(false);

        final Span span = new Span();
        span.setId(SPAN_ID);
        span.setTrace_id(TRACE_ID);
        span.setName(SPAN_NAME);

        // Should not throw exception but log error.
        final ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT, params);

        scribeSpanCollector.collect(span);

        final ScribeServer server = new ScribeServer(PORT);
        server.start();
        try {
            scribeSpanCollector.collect(span);
            scribeSpanCollector.close();

            assertEquals(2, server.getReceivedSpans().size());
        } finally {
            server.stop();
        }

    }

}

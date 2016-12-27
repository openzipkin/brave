package com.github.kristofa.brave.scribe;

import static org.junit.Assert.assertEquals;

import com.github.kristofa.brave.SpanId;
import org.apache.thrift.transport.TTransportException;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ITScribeSpanCollectorFailOnSetup {

    private static final int PORT = FreePortProvider.getNewFreePort();
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;

    /**
     * Integration isSampled that checks failOnSetup = false. The isSampled basically shows that no exception is thrown when the server
     * is down when initializing but we are still able to reconnect in case the server gets up at a later stage.
     * 
     * @throws TTransportException
     * @throws InterruptedException
     */
    @Test
    public void testFailOnSetupFalse() throws TTransportException, InterruptedException {
        final ScribeSpanCollectorParams params = new ScribeSpanCollectorParams();
        params.setFailOnSetup(false);

        final Span span = Span.create(SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).build());

        // Should not throw exception but log error.
        final ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT, params);

        scribeSpanCollector.collect(span);

        // Sleep a small amount to give the collector time to process
        // the span.
        Thread.sleep(100);

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

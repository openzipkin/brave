package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;

import org.apache.thrift.transport.TTransportException;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ITZipkinSpanCollectorFailOnSetup {

    private static final int PORT = 9210;
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
        final ZipkinSpanCollectorParams params = new ZipkinSpanCollectorParams();
        params.setFailOnSetup(false);

        final Span span = new Span();
        span.setId(SPAN_ID);
        span.setTrace_id(TRACE_ID);
        span.setName(SPAN_NAME);

        // Should not throw exception but log error.
        final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT, params);

        zipkinSpanCollector.collect(span);

        final ZipkinCollectorServer server = new ZipkinCollectorServer(PORT);
        server.start();
        try {
            zipkinSpanCollector.collect(span);
            zipkinSpanCollector.close();

            assertEquals(2, server.getReceivedSpans().size());
        } finally {
            server.stop();
        }

    }

}

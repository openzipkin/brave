package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.thrift.transport.TTransportException;
import org.junit.Test;

import com.twitter.zipkin.gen.Span;

public class ZipkinSpanCollectorTest {

    private static final int PORT = 9410;
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;
    private static final String SPAN_NAME = "SpanName";

    @Test(expected = IllegalArgumentException.class)
    public void testZipkinSpanCollector() {
        new ZipkinSpanCollector("", PORT);
    }

    @Test
    public void testCollect() throws TTransportException {

        final ZipkinCollectorServer zipkinCollectorServer = new ZipkinCollectorServer(PORT);
        zipkinCollectorServer.start();
        try {

            final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT);
            try {
                final Span span = new Span();
                span.setId(SPAN_ID);
                span.setTrace_id(TRACE_ID);
                span.setName(SPAN_NAME);
                zipkinSpanCollector.collect(span);

            } finally {
                zipkinSpanCollector.close();
            }
            final List<Span> serverCollectedSpans = zipkinCollectorServer.getReceivedSpans();
            assertEquals(1, serverCollectedSpans.size());
            assertEquals(SPAN_ID, serverCollectedSpans.get(0).getId());
            assertEquals(TRACE_ID, serverCollectedSpans.get(0).getTrace_id());
            assertEquals(SPAN_NAME, serverCollectedSpans.get(0).getName());

        } finally {
            zipkinCollectorServer.stop();
        }
    }

}

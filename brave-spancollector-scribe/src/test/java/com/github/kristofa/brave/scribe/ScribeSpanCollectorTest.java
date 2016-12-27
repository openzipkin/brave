package com.github.kristofa.brave.scribe;

import static org.junit.Assert.assertEquals;

import com.github.kristofa.brave.SpanId;
import java.util.List;

import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

public class ScribeSpanCollectorTest {

    private static final int PORT = 9500;
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;
    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";

    private static ScribeServer scribeServer;

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
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZipkinSpanCollector() {
        new ScribeSpanCollector("", PORT);
    }

    @Test
    public void testCollect() throws TTransportException {

        final ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT);
        try {
            Span span = Span.create(SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).build());

            scribeSpanCollector.collect(span);

        } finally {
            scribeSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = scribeServer.getReceivedSpans();
        assertEquals(1, serverCollectedSpans.size());
        assertEquals(SPAN_ID, serverCollectedSpans.get(0).getId());
        assertEquals(TRACE_ID, serverCollectedSpans.get(0).getTrace_id());
    }

    @Test
    public void testCollectWithDefaultAnnotation() throws TTransportException {

        final ScribeSpanCollector scribeSpanCollector = new ScribeSpanCollector("localhost", PORT);
        scribeSpanCollector.addDefaultAnnotation(KEY1, VALUE1);
        try {
            Span span = Span.create(SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).build());

            scribeSpanCollector.collect(span);

        } finally {
            scribeSpanCollector.close();
        }
        final List<Span> serverCollectedSpans = scribeServer.getReceivedSpans();
        assertEquals(1, serverCollectedSpans.size());
        final Span span = serverCollectedSpans.get(0);
        assertEquals(SPAN_ID, span.getId());
        assertEquals(TRACE_ID, span.getTrace_id());
        final List<BinaryAnnotation> binary_annotations = span.getBinary_annotations();
        assertEquals("Expect default annotation to have been submitted.", 1, binary_annotations.size());
        final BinaryAnnotation binaryAnnotation = binary_annotations.get(0);
        assertEquals(KEY1, binaryAnnotation.getKey());
        assertEquals(VALUE1, new String(binaryAnnotation.getValue()));

    }

}

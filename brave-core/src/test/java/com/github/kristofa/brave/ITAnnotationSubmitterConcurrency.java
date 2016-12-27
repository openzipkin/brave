package com.github.kristofa.brave;

import com.github.kristofa.brave.AnnotationSubmitter.DefaultClock;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This isSampled proves that we have proper synchronisation when submitted annotations for the same span. Without proper
 * synchronisation this isSampled fails with {@link ArrayIndexOutOfBoundsException} because adding items to an {@link ArrayList}
 * is not thread-safe. </p> This isSampled proves that we fixed the threading bug.
 * 
 * @author kristof
 */
public class ITAnnotationSubmitterConcurrency {

    private ExecutorService executorService;
    Span span = Span.create(SpanId.builder().spanId(1L).build());
    private Endpoint endpoint =
        Endpoint.builder().serviceName("foobar").ipv4(127 << 24 | 1).port(9999).build();

    @Before
    public void setup() {
        executorService = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    @Test
    public void testSubmitAnnotations() throws InterruptedException, ExecutionException {
        CurrentSpan currentSpan = new CurrentSpan() {
            @Override Span get() {
                return span;
            }
        };
        final AnnotationSubmitter annotationSubmitter =
            AnnotationSubmitter.create(currentSpan, endpoint, new DefaultClock());

        final List<AnnotationSubmitThread> threadList =
            Arrays.asList(new AnnotationSubmitThread(1, 100, annotationSubmitter), new AnnotationSubmitThread(101, 200,
                annotationSubmitter), new AnnotationSubmitThread(201, 300, annotationSubmitter),
                new AnnotationSubmitThread(301, 400, annotationSubmitter));

        final List<Future<?>> resultList = new ArrayList<Future<?>>();

        for (final AnnotationSubmitThread thread : threadList) {
            {
                resultList.add(executorService.submit(thread));
            }
        }

        for (final Future<?> result : resultList) {
            result.get();
        }

        assertEquals(400, span.getAnnotations().size());
        assertEquals(400, span.getBinary_annotations().size());

    }

    private final class AnnotationSubmitThread implements Callable<Void> {

        private final int from;
        private final int to;
        private final AnnotationSubmitter annotationSubmitter;

        public AnnotationSubmitThread(final int from, final int to, final AnnotationSubmitter annotationSubmitter) {
            assertTrue(from <= to);
            this.from = from;
            this.to = to;
            this.annotationSubmitter = annotationSubmitter;
        }

        @Override
        public Void call() throws Exception {
            for (int index = from; index <= to; index++) {
                annotationSubmitter.submitAnnotation("annotation" + index);
                annotationSubmitter.submitBinaryAnnotation("binaryAnnotation" + index, "value");
            }
            return null;
        }

    }
}

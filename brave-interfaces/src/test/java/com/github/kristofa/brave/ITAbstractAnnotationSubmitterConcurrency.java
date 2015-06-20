package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.Validate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * This test proves that we have proper synchronisation when submitted annotations for the same span. Without proper
 * synchronisation this test fails with {@link ArrayIndexOutOfBoundsException} because adding items to an {@link ArrayList}
 * is not thread-safe. </p> This test proves that we fixed the threading bug.
 * 
 * @author kristof
 */
public class ITAbstractAnnotationSubmitterConcurrency {

    private ExecutorService executorService;
    private Span span;

    @Before
    public void setup() {
        executorService = Executors.newFixedThreadPool(4);
        span = new Span();
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    @Test
    public void testSubmitAnnotations() throws InterruptedException, ExecutionException {

        final MockAnnotationSubmitter mockAnnotationSubmitter = new MockAnnotationSubmitter();

        final List<AnnotationSubmitThread> threadList =
            Arrays.asList(new AnnotationSubmitThread(1, 100, mockAnnotationSubmitter), new AnnotationSubmitThread(101, 200,
                mockAnnotationSubmitter), new AnnotationSubmitThread(201, 300, mockAnnotationSubmitter),
                new AnnotationSubmitThread(301, 400, mockAnnotationSubmitter));

        final List<Future<?>> resultList = new ArrayList<Future<?>>();

        for (final AnnotationSubmitThread thread : threadList) {
            {
                resultList.add(executorService.submit(thread));
            }
        }

        for (final Future<?> result : resultList) {
            result.get();
        }

        assertEquals(800, span.getAnnotations().size());
        assertEquals(400, span.getBinary_annotations().size());

    }

    private final class MockAnnotationSubmitter extends AbstractAnnotationSubmitter {

        @Override
        Span getSpan() {
            return span;
        }

        @Override
        Endpoint getEndPoint() {
            return null;
        }

    }

    private final class AnnotationSubmitThread implements Callable<Void> {

        private final int from;
        private final int to;
        private final AbstractAnnotationSubmitter annotationSubmitter;

        public AnnotationSubmitThread(final int from, final int to, final AbstractAnnotationSubmitter annotationSubmitter) {
            Validate.isTrue(from <= to);
            this.from = from;
            this.to = to;
            this.annotationSubmitter = annotationSubmitter;
        }

        @Override
        public Void call() throws Exception {
            for (int index = from; index <= to; index++) {
                annotationSubmitter.submitAnnotation("annotation" + index);
                annotationSubmitter.submitAnnotation("annotationWithTime" + index, 0, 5);
                annotationSubmitter.submitBinaryAnnotation("binaryAnnotation" + index, "value");
            }
            return null;
        }

    }
}

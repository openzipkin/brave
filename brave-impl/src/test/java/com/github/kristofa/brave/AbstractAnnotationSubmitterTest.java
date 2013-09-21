package com.github.kristofa.brave;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.UnsupportedEncodingException;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

public class AbstractAnnotationSubmitterTest {

    private final static String ANNOTATION_NAME = "AnnotationName";
    protected static final long CURRENT_TIME = 20;
    private static final String KEY = "key";
    private static final String STRING_VALUE = "stringValue";
    private static final int INT_VALUE = 23;

    private AbstractAnnotationSubmitter abstractAnnotationSubmitter;
    private Endpoint endPoint;
    private Span mockSpan;

    @Before
    public void setup() {
        endPoint = new Endpoint();
        mockSpan = mock(Span.class);
        abstractAnnotationSubmitter = new AbstractAnnotationSubmitter() {

            @Override
            Span getSpan() {
                return mockSpan;
            }

            @Override
            Endpoint getEndPoint() {
                return endPoint;
            }

            @Override
            long currentTimeMicroseconds() {
                return CURRENT_TIME;
            }
        };
    }

    @Test
    public void testSubmitAnnotationStartEndTime() {
        final long startDateMs = 1000;
        final long endDateMs = 2000;
        final int durationMs = (int)(endDateMs - startDateMs);
        abstractAnnotationSubmitter.submitAnnotation(ANNOTATION_NAME, startDateMs, endDateMs);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(ANNOTATION_NAME + "=" + durationMs + "ms");
        expectedAnnotation.setTimestamp(startDateMs * 1000);
        expectedAnnotation.setDuration(durationMs * 1000);
        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testSubmitAnnotationSpanEndpointString() {
        abstractAnnotationSubmitter.submitAnnotation(ANNOTATION_NAME);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(ANNOTATION_NAME);
        expectedAnnotation.setTimestamp(CURRENT_TIME);

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitBinaryAnnotationStringValueEmptyKey() throws UnsupportedEncodingException {
        abstractAnnotationSubmitter.submitBinaryAnnotation(" ", STRING_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void testSubmitBinaryAnnotationStringValueNullValue() throws UnsupportedEncodingException {
        abstractAnnotationSubmitter.submitBinaryAnnotation(KEY, null);

    }

    @Test
    public void testSubmitBinaryAnnotationStringValue() throws UnsupportedEncodingException {
        abstractAnnotationSubmitter.submitBinaryAnnotation(KEY, STRING_VALUE);

        final BinaryAnnotation expectedAnnodation = new BinaryAnnotation();
        expectedAnnodation.setHost(endPoint);
        expectedAnnodation.setKey(KEY);
        expectedAnnodation.setValue(STRING_VALUE.getBytes("UTF-8"));
        expectedAnnodation.setAnnotation_type(AnnotationType.STRING);

        verify(mockSpan).addToBinary_annotations(expectedAnnodation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testSubmitBinaryAnnotationIntValue() {
        abstractAnnotationSubmitter.submitBinaryAnnotation(KEY, INT_VALUE);

        final BinaryAnnotation expectedAnnodation = new BinaryAnnotation();
        expectedAnnodation.setHost(endPoint);
        expectedAnnodation.setKey(KEY);
        expectedAnnodation.setValue(String.valueOf(INT_VALUE).getBytes());
        expectedAnnodation.setAnnotation_type(AnnotationType.STRING);

        verify(mockSpan).addToBinary_annotations(expectedAnnodation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testCurrentTimeMicroSeconds() throws InterruptedException {
        final AbstractAnnotationSubmitter anotherAbstractAnnotationSubmitter = new AbstractAnnotationSubmitter() {

            @Override
            Span getSpan() {
                return null;
            }

            @Override
            Endpoint getEndPoint() {
                return null;
            }
        };

        final long start = anotherAbstractAnnotationSubmitter.currentTimeMicroseconds();
        Thread.sleep(130);
        final long end = anotherAbstractAnnotationSubmitter.currentTimeMicroseconds();
        assertTrue(end - start > 1000);

    }

}

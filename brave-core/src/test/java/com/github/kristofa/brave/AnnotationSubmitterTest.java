package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.StaticSpanAndEndpoint;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class AnnotationSubmitterTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;

    private final static String ANNOTATION_NAME = "AnnotationName";
    private static final String KEY = "key";
    private static final String STRING_VALUE = "stringValue";
    private static final int INT_VALUE = 23;

    private AnnotationSubmitter annotationSubmitter;
    private Endpoint endpoint;
    private Span mockSpan;

    @Before
    public void setup() {
        mockSpan = mock(Span.class);
        endpoint = new Endpoint();
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        annotationSubmitter = AnnotationSubmitter.create(StaticSpanAndEndpoint.create(mockSpan, endpoint));
    }

    @Test
    public void testSubmitAnnotationStartEndTime() {
        final long startDateMs = 1000;
        final long endDateMs = 2000;
        final int durationMs = (int)(endDateMs - startDateMs);
        annotationSubmitter.submitAnnotation(ANNOTATION_NAME, startDateMs, endDateMs);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endpoint);
        expectedAnnotation.setValue(ANNOTATION_NAME + "=" + durationMs + "ms");
        expectedAnnotation.setTimestamp(startDateMs * 1000);
        expectedAnnotation.setDuration(durationMs * 1000);
        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testSubmitAnnotationSpanEndpointString() {
        annotationSubmitter.submitAnnotation(ANNOTATION_NAME);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endpoint);
        expectedAnnotation.setValue(ANNOTATION_NAME);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitBinaryAnnotationStringValueEmptyKey() {
        annotationSubmitter.submitBinaryAnnotation(" ", STRING_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void testSubmitBinaryAnnotationStringValueNullValue() {
        annotationSubmitter.submitBinaryAnnotation(KEY, null);

    }

    @Test
    public void testSubmitBinaryAnnotationStringValue() throws UnsupportedEncodingException {
        annotationSubmitter.submitBinaryAnnotation(KEY, STRING_VALUE);

        final BinaryAnnotation expectedAnnodation = new BinaryAnnotation();
        expectedAnnodation.setHost(endpoint);
        expectedAnnodation.setKey(KEY);
        expectedAnnodation.setValue(STRING_VALUE.getBytes("UTF-8"));
        expectedAnnodation.setAnnotation_type(AnnotationType.STRING);

        verify(mockSpan).addToBinary_annotations(expectedAnnodation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testSubmitBinaryAnnotationIntValue() {
        annotationSubmitter.submitBinaryAnnotation(KEY, INT_VALUE);

        final BinaryAnnotation expectedAnnodation = new BinaryAnnotation();
        expectedAnnodation.setHost(endpoint);
        expectedAnnodation.setKey(KEY);
        expectedAnnodation.setValue(String.valueOf(INT_VALUE).getBytes());
        expectedAnnodation.setAnnotation_type(AnnotationType.STRING);

        verify(mockSpan).addToBinary_annotations(expectedAnnodation);
        verifyNoMoreInteractions(mockSpan);
    }

    @Test
    public void testCurrentTimeMicroSeconds() {
        AnnotationSubmitter anotherAnnotationSubmitter = AnnotationSubmitter.create(
            StaticSpanAndEndpoint.create(null, null));
        assertEquals(CURRENT_TIME_MICROSECONDS, anotherAnnotationSubmitter.currentTimeMicroseconds());
    }
}

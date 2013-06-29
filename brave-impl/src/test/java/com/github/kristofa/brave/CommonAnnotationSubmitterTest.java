package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

public class CommonAnnotationSubmitterTest {

    private final static String ANNOTATION_NAME = "AnnotationName";
    private final static int DURATION = 11;
    protected static final long CURRENT_TIME = 20;

    private CommonAnnotationSubmitter commonAnnotationSubmitter;
    private Endpoint endPoint;
    private Span mockSpan;

    @Before
    public void setup() {
        commonAnnotationSubmitter = new CommonAnnotationSubmitter() {

            @Override
            long currentTimeMicroseconds() {
                return CURRENT_TIME;
            }
        };
        endPoint = new Endpoint();
        mockSpan = mock(Span.class);
    }

    @Test
    public void testSubmitAnnotationSpanEndpointStringInt() {
        commonAnnotationSubmitter.submitAnnotation(mockSpan, endPoint, ANNOTATION_NAME, DURATION);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(ANNOTATION_NAME);
        expectedAnnotation.setTimestamp(CURRENT_TIME);
        expectedAnnotation.setDuration(DURATION * 1000);

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);

    }

    @Test
    public void testSubmitAnnotationSpanEndpointString() {
        commonAnnotationSubmitter.submitAnnotation(mockSpan, endPoint, ANNOTATION_NAME);

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(ANNOTATION_NAME);
        expectedAnnotation.setTimestamp(CURRENT_TIME);

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockSpan);
    }

}

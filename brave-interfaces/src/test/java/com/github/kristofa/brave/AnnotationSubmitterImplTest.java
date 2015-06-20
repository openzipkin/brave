package com.github.kristofa.brave;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

public class AnnotationSubmitterImplTest {

    private AnnotationSubmitterImpl annotationSubmitter;
    private ServerSpanState mockState;
    private Span mockSpan;
    private Endpoint mockEndPoint;

    @Before
    public void setup() {
        mockState = mock(ServerSpanState.class);
        final ServerSpan mockServerSpan = mock(ServerSpan.class);
        mockSpan = mock(Span.class);
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockState.getCurrentServerSpan()).thenReturn(mockServerSpan);

        mockEndPoint = mock(Endpoint.class);
        when(mockState.getServerEndPoint()).thenReturn(mockEndPoint);

        annotationSubmitter = new AnnotationSubmitterImpl(mockState);
    }

    @Test
    public void testGetSpan() {
        assertSame(mockSpan, annotationSubmitter.getSpan());
    }

    @Test
    public void testGetEndPoint() {
        assertSame(mockEndPoint, annotationSubmitter.getEndPoint());
    }

    @Test(expected = NullPointerException.class)
    public void testAnnotationSubmitterImpl() {
        new AnnotationSubmitterImpl(null);
    }

}

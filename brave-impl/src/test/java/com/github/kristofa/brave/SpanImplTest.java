package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class SpanImplTest {

    private final static long TRACE_ID = 1;
    private final static long SPAN_ID_VALUE = 2;
    private final static Long PARENT_SPAN_ID = new Long(3);
    private final static long TRACE_ID_2 = 4;
    private final static SpanId SPAN_ID = new SpanIdImpl(TRACE_ID, SPAN_ID_VALUE, PARENT_SPAN_ID);
    private final static SpanId SPAN_ID_2 = new SpanIdImpl(TRACE_ID_2, SPAN_ID_VALUE, PARENT_SPAN_ID);
    private final static String SPAN_NAME = "Span name";
    private final static String SPAN_NAME_2 = "Span name 2";

    private final static String ANNOTATION_NAME = "Annotation name";
    private final static String ANNOTATION_NAME_2 = "Annotation name 2";
    private final static EndPoint END_POINT = mock(EndPoint.class);

    private SpanImpl span;

    @Before
    public void setup() {
        span = new SpanImpl(SPAN_ID, SPAN_NAME);
    }

    @Test
    public void testHashCode() {
        final SpanImpl equalSpan = new SpanImpl(SPAN_ID, SPAN_NAME);
        assertEquals(span.hashCode(), equalSpan.hashCode());
    }

    @Test(expected = NullPointerException.class)
    public void testSpanImplNullSpanId() {
        new SpanImpl(null, SPAN_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSpanImplEmptySpanName() {
        new SpanImpl(SPAN_ID, "");
    }

    @Test
    public void testGetSpanId() {
        assertSame(SPAN_ID, span.getSpanId());
    }

    @Test
    public void testGetName() {
        assertEquals(SPAN_NAME, span.getName());
    }

    @Test
    public void testGetAnnotations() {
        assertTrue(span.getAnnotations().isEmpty());
    }

    @Test
    public void testAddAnnotation() {
        final AnnotationImpl annotationImpl = new AnnotationImpl(ANNOTATION_NAME, END_POINT);
        final AnnotationImpl annotationImpl2 = new AnnotationImpl(ANNOTATION_NAME_2, END_POINT);
        span.addAnnotation(annotationImpl);
        span.addAnnotation(annotationImpl2);
        final Set<Annotation> annotations = span.getAnnotations();
        assertEquals(2, annotations.size());
        assertTrue(annotations.contains(annotationImpl));
        assertTrue(annotations.contains(annotationImpl2));

    }

    @Test
    public void testEqualsObject() {
        final SpanImpl equalSpan = new SpanImpl(SPAN_ID, SPAN_NAME);
        assertTrue(span.equals(equalSpan));
        assertTrue(span.equals(span));
        assertFalse(span.equals(null));
        assertFalse(span.equals(new String()));

        final SpanImpl nonEqualSpan = new SpanImpl(SPAN_ID_2, SPAN_NAME);
        assertFalse(span.equals(nonEqualSpan));
        final SpanImpl nonEqualSpan2 = new SpanImpl(SPAN_ID, SPAN_NAME_2);
        assertFalse(span.equals(nonEqualSpan2));

    }

    @Test
    public void testToString() {
        assertEquals("[id: " + SPAN_ID.toString() + ", name: " + SPAN_NAME + ", annotations: []]", span.toString());
    }

}

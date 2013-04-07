package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

public class AnnotationImplTest {

    private final static Long DURATION = 15654l;
    private final static String ANNOTATION_NAME = "annotation Name";
    private final static EndPoint MOCK_ENDPOINT = mock(EndPoint.class);

    private AnnotationImpl annotation;

    @Before
    public void setup() {
        annotation = new AnnotationImpl(ANNOTATION_NAME, MOCK_ENDPOINT, DURATION);
    }

    @Test
    public void testHashCode() {
        final AnnotationImpl equalAnnotation = new AnnotationImpl(ANNOTATION_NAME, MOCK_ENDPOINT, DURATION);
        assertEquals("Equal objects should have same hash code.", annotation.hashCode(), equalAnnotation.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyAnnotationName() {
        new AnnotationImpl("", MOCK_ENDPOINT);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullEndPoint() {
        new AnnotationImpl(ANNOTATION_NAME, null);
    }

    @Test
    public void testGetTimeStamp() throws InterruptedException {
        Thread.sleep(10);
        final Annotation annotation2 = new AnnotationImpl(ANNOTATION_NAME, MOCK_ENDPOINT, DURATION);
        final long timeStamp1 = annotation.getTimeStamp();
        assertTrue(timeStamp1 > 0);
        final long timeStamp2 = annotation2.getTimeStamp();
        assertTrue(timeStamp2 > timeStamp1);
    }

    @Test
    public void testGetAnnotationName() {
        assertEquals(ANNOTATION_NAME, annotation.getAnnotationName());
    }

    @Test
    public void testGetDuration() {
        assertEquals(DURATION, annotation.getDuration());
    }

    @Test
    public void testGetEndPoint() {
        assertEquals(MOCK_ENDPOINT, annotation.getEndPoint());
    }

    @Test
    public void testEqualsObject() {
        final AnnotationImpl equalAnnotation = new AnnotationImpl(ANNOTATION_NAME, MOCK_ENDPOINT, DURATION);
        assertTrue(annotation.equals(equalAnnotation));
        assertTrue(annotation.equals(annotation));
        assertFalse(annotation.equals(null));
        assertFalse(annotation.equals(new String()));

        final AnnotationImpl nonEqualAnnotation = new AnnotationImpl(ANNOTATION_NAME + "a", MOCK_ENDPOINT, DURATION);
        assertFalse(annotation.equals(nonEqualAnnotation));
        final AnnotationImpl nonEqualAnnotation2 = new AnnotationImpl(ANNOTATION_NAME, mock(EndPoint.class), DURATION);
        assertFalse(annotation.equals(nonEqualAnnotation2));
        final AnnotationImpl nonEqualAnnotation3 = new AnnotationImpl(ANNOTATION_NAME, MOCK_ENDPOINT, DURATION + 1);
        assertFalse(annotation.equals(nonEqualAnnotation3));
    }

}

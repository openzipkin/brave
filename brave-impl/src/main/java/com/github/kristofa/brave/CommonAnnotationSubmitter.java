package com.github.kristofa.brave;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * Used to submit annotations to a Span. Can be shared by {@link ClientTracer} and {@link ServerTracer}.
 * 
 * @author adriaens
 */
class CommonAnnotationSubmitter {

    private static final String UTF_8 = "UTF-8";

    /**
     * Submit an annotation with start/end time. Used for timing an event.
     * 
     * @param span Span.
     * @param endpoint Endpoint, optional, can be <code>null</code>.
     * @param annotationName Annotation name.
     * @param startDate Start date/time, Unix time in milliseconds. eg System.currentTimeMillis()
     * @param endDate End date/time, Unix time in milliseconds. eg System.currentTimeMillis()
     * @param duration Duration in milliseconds.
     */
    public void submitAnnotation(final Span span, final Endpoint endpoint, final String annotationName,
        final long startDate, final long endDate) {
        final Annotation annotation = new Annotation();
        final int duration = (int)(endDate - startDate);
        annotation.setTimestamp(startDate * 1000);
        annotation.setHost(endpoint);
        annotation.setDuration(duration * 1000);
        // Duration is currently not supported in the ZipkinUI, so also add it as part of the annotation name.
        annotation.setValue(annotationName + "=" + duration + "ms");
        span.addToAnnotations(annotation);
    }

    /**
     * Submit an annotation without duration.
     * 
     * @param span Span
     * @param endPoint Endpoint, optional, can be <code>null</code>.
     * @param annotationName Annotation name.
     */
    public void submitAnnotation(final Span span, final Endpoint endPoint, final String annotationName) {
        final Annotation annotation = new Annotation();
        annotation.setTimestamp(currentTimeMicroseconds());
        annotation.setHost(endPoint);
        annotation.setValue(annotationName);
        span.addToAnnotations(annotation);
    }

    /**
     * Submits a binary annoration.
     * 
     * @param span Span.
     * @param endPoint Endpoint, optional, can be <code>null</code>.
     * @param key Key, should not be empty.
     * @param value Value, should not be <code>null</code>.
     */
    public void submitBinaryAnnotation(final Span span, final Endpoint endPoint, final String key, final String value) {
        Validate.notNull(value);
        try {
            final ByteBuffer bb = ByteBuffer.wrap(value.getBytes(UTF_8));
            submitBinaryAnnotation(span, endPoint, key, bb, AnnotationType.STRING);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Submits a binary annotation.
     * 
     * @param span Span.
     * @param endPoint Endpoint, optional, can be <code>null</code>.
     * @param key Key, should not be empty.
     * @param value int value.
     */
    public void submitBinaryAnnotation(final Span span, final Endpoint endPoint, final String key, final int value) {
        final ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(value);
        submitBinaryAnnotation(span, endPoint, key, bb, AnnotationType.I32);
    }

    /**
     * Submits a binary annotation with custom type.
     * 
     * @param span Span.
     * @param endPoint Endpoint, optional, can be <code>null</code>.
     * @param key Key, should not be empty.
     * @param value Should not be null.
     * @param annotationType Indicates the type of the value.
     */
    public void submitBinaryAnnotation(final Span span, final Endpoint endPoint, final String key, final ByteBuffer value,
        final AnnotationType annotationType) {
        Validate.notBlank(key);
        Validate.notNull(value);
        final BinaryAnnotation binaryAnnotation = new BinaryAnnotation();
        binaryAnnotation.setKey(key);
        binaryAnnotation.setValue(value);
        binaryAnnotation.setAnnotation_type(annotationType);
        binaryAnnotation.setHost(endPoint);
        span.addToBinary_annotations(binaryAnnotation);
    }

    /**
     * Gets the current time in micro seconds.
     * 
     * @return Current time in micro seconds.
     */
    long currentTimeMicroseconds() {
        return System.currentTimeMillis() * 1000;
    }

}

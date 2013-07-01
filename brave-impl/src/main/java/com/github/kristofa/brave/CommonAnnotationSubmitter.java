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

    /**
     * Submit an annotation with duration.
     * 
     * @param span Span
     * @param endPoint Endpoint, optional, can be <code>null</code>.
     * @param annotationName Annotation name.
     * @param duration Duration in milliseconds.
     */
    public void submitAnnotation(final Span span, final Endpoint endPoint, final String annotationName, final int duration) {

        final Annotation annotation = new Annotation();
        if (duration >= 0) {
            annotation.setDuration(duration * 1000);
        }
        annotation.setTimestamp(currentTimeMicroseconds());
        annotation.setHost(endPoint);
        annotation.setValue(annotationName);
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
        submitAnnotation(span, endPoint, annotationName, -1);
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
        Validate.notBlank(key);
        Validate.notNull(value);
        final BinaryAnnotation binaryAnnotation = new BinaryAnnotation();
        binaryAnnotation.setKey(key);
        try {
            binaryAnnotation.setValue(ByteBuffer.wrap(value.getBytes("UTF-8")));
            binaryAnnotation.setAnnotation_type(AnnotationType.STRING);
            binaryAnnotation.setHost(endPoint);
            span.addToBinary_annotations(binaryAnnotation);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
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

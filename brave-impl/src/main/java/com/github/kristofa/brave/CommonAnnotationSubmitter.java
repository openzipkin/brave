package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
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
     * @param endPoint Endpoint, optional, an be <code>null</code>.
     * @param annotationName Annotation name.
     */
    public void submitAnnotation(final Span span, final Endpoint endPoint, final String annotationName) {
        submitAnnotation(span, endPoint, annotationName, -1);
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

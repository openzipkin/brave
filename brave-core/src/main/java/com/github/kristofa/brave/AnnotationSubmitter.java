package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Used to submit application specific annotations.
 * 
 * @author kristof
 */
public abstract class AnnotationSubmitter {

    public static AnnotationSubmitter create(SpanAndEndpoint spanAndEndpoint) {
        return new AnnotationSubmitterImpl(spanAndEndpoint);
    }

    abstract SpanAndEndpoint spanAndEndpoint();

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Submits custom annotation that represents an event with duration.
     *
     * @param annotationName Custom annotation.
     * @param startTime Start time, <a href="http://en.wikipedia.org/wiki/Unix_time">Unix time</a> in milliseconds. eg
     *            System.currentTimeMillis().
     * @param endTime End time, Unix time in milliseconds. eg System.currentTimeMillis().
     */
    public void submitAnnotation(String annotationName, long startTime, long endTime) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            Annotation annotation = new Annotation();
            int duration = (int)(endTime - startTime);
            annotation.setTimestamp(startTime * 1000);
            annotation.setHost(spanAndEndpoint().endpoint());
            annotation.setDuration(duration * 1000);
            // Duration is currently not supported in the ZipkinUI, so also add it as part of the annotation name.
            annotation.setValue(annotationName + "=" + duration + "ms");
            addAnnotation(span, annotation);
        }
    }

    /**
     * Submits custom annotation for current span. Use this method if your annotation has no duration assigned to it.
     *
     * @param annotationName Custom annotation for current span.
     */
    public void submitAnnotation(String annotationName) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            Annotation annotation = new Annotation();
            annotation.setTimestamp(currentTimeMicroseconds());
            annotation.setHost(spanAndEndpoint().endpoint());
            annotation.setValue(annotationName);
            addAnnotation(span, annotation);
        }
    }

    /**
     * Submits a binary (key/value) annotation with String value.
     *
     * @param key Key, should not be blank.
     * @param value String value, should not be <code>null</code>.
     */
    public void submitBinaryAnnotation(String key, String value) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            checkNotNull(value, "Null value");
            ByteBuffer bb = ByteBuffer.wrap(value.getBytes(UTF_8));
            submitBinaryAnnotation(span, spanAndEndpoint().endpoint(), key, bb, AnnotationType.STRING);
        }
    }

    /**
     * Submits a binary (key/value) annotation with int value.
     *
     * @param key Key, should not be blank.
     * @param value Integer value.
     */
    public void submitBinaryAnnotation(String key, int value) {
        submitBinaryAnnotation(key, String.valueOf(value));

        // This code did not work in the UI, it looks like UI only supports String annotations.
        // ByteBuffer bb = ByteBuffer.allocate(4);
        // bb.putInt(value);
        // submitBinaryAnnotation(span, endpoint, key, bb, AnnotationType.I32);

    }

    /**
     * Submits a binary annotation with custom type.
     *
     * @param span Span.
     * @param endpoint Endpoint, optional, can be <code>null</code>.
     * @param key Key, should not be empty.
     * @param value Should not be null.
     * @param annotationType Indicates the type of the value.
     */
    private void submitBinaryAnnotation(Span span, Endpoint endpoint, String key, ByteBuffer value,
                                        AnnotationType annotationType) {
        checkNotBlank(key, "Null or blank key");
        checkNotNull(value, "Null value");
        BinaryAnnotation binaryAnnotation = new BinaryAnnotation();
        binaryAnnotation.setKey(key);
        binaryAnnotation.setValue(value);
        binaryAnnotation.setAnnotation_type(annotationType);
        binaryAnnotation.setHost(endpoint);
        addBinaryAnnotation(span, binaryAnnotation);
    }

    long currentTimeMicroseconds() {
        return System.currentTimeMillis() * 1000;
    }

    private void addAnnotation(Span span, Annotation annotation) {
        synchronized (span) {
            span.addToAnnotations(annotation);
        }
    }

    private void addBinaryAnnotation(Span span, BinaryAnnotation ba) {
        synchronized (span) {
            span.addToBinary_annotations(ba);
        }
    }

    AnnotationSubmitter() {
    }

    private static final class AnnotationSubmitterImpl extends AnnotationSubmitter {

        private final SpanAndEndpoint spanAndEndpoint;

        private AnnotationSubmitterImpl(SpanAndEndpoint spanAndEndpoint) {
            this.spanAndEndpoint = checkNotNull(spanAndEndpoint, "Null spanAndEndpoint");
        }

        @Override
        SpanAndEndpoint spanAndEndpoint() {
            return spanAndEndpoint;
        }
    }
}

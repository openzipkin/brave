package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

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

    /**
     * Associates an event that explains latency with the current system time.
     *
     * @param value A short tag indicating the event, like "finagle.retry"
     */
    public void submitAnnotation(String value) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            Annotation annotation = new Annotation();
            annotation.setTimestamp(currentTimeMicroseconds());
            annotation.setHost(spanAndEndpoint().endpoint());
            annotation.setValue(value);
            addAnnotation(span, annotation);
        }
    }

    /**
     * Associates an event that explains latency with a timestamp.
     *
     * <p/> This is an alternative to {@link #submitAnnotation(String)}, when
     * you have a timestamp more precise or accurate than {@link System#currentTimeMillis()}.
     *
     * @param value     A short tag indicating the event, like "finagle.retry"
     * @param timestamp microseconds from epoch
     */
    public void submitAnnotation(String value, long timestamp) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            Annotation annotation = new Annotation();
            annotation.setTimestamp(timestamp);
            annotation.setHost(spanAndEndpoint().endpoint());
            annotation.setValue(value);
            addAnnotation(span, annotation);
        }
    }

    /** This adds an annotation that corresponds with {@link Span#getTimestamp()} */
    void submitStartAnnotation(String annotationName) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            Annotation annotation = new Annotation();
            annotation.setTimestamp(currentTimeMicroseconds());
            annotation.setHost(spanAndEndpoint().endpoint());
            annotation.setValue(annotationName);
            synchronized (span) {
                span.setTimestamp(annotation.getTimestamp());
                span.addToAnnotations(annotation);
            }
        }
    }

    /**
     * This adds an annotation that corresponds with {@link Span#getDuration()}, and sends the span
     * for collection.
     *
     * @return true if a span was sent for collection.
     */
    boolean submitEndAnnotation(String annotationName, SpanCollector spanCollector) {
        Span span = spanAndEndpoint().span();
        if (span == null) {
          return false;
        }
        Annotation annotation = new Annotation();
        annotation.setTimestamp(currentTimeMicroseconds());
        annotation.setHost(spanAndEndpoint().endpoint());
        annotation.setValue(annotationName);
        span.addToAnnotations(annotation);
        span.setDuration(annotation.getTimestamp() - span.getTimestamp());
        spanCollector.collect(span);
        return true;
    }

    /**
     * Internal api for submitting an address. Until a naming function is added, this coerces null
     * {@code serviceName} to "unknown", as that's zipkin's convention.
     *
     * @param ipv4        ipv4 host address as int. Ex for the ip 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
     * @param port        Port for service
     * @param serviceName Name of service. Should be lowercase and not empty. {@code null} will coerce to "unknown", as that's zipkin's convention.
     */
    void submitAddress(String key, int ipv4, int port, @Nullable String serviceName) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            serviceName = serviceName != null ? serviceName : "unknown";
            Endpoint endpoint = new Endpoint(ipv4, (short) port, serviceName);
            BinaryAnnotation ba = new BinaryAnnotation();
            ba.setKey(key);
            ba.setValue(new byte[]{1});
            ba.setAnnotation_type(AnnotationType.BOOL);
            ba.setHost(endpoint);
            addBinaryAnnotation(span, ba);
        }
    }

    /**
     * Binary annotations are tags applied to a Span to give it context. For
     * example, a key "your_app.version" would let you lookup spans by version.
     *
     * @param key Name used to lookup spans, such as "your_app.version"
     * @param value String value, should not be <code>null</code>.
     */
    public void submitBinaryAnnotation(String key, String value) {
        Span span = spanAndEndpoint().span();
        if (span != null) {
            BinaryAnnotation ba = new BinaryAnnotation(key, value, spanAndEndpoint().endpoint());
            addBinaryAnnotation(span, ba);
        }
    }

    /**
     * Submits a binary (key/value) annotation with int value.
     *
     * @param key Key, should not be blank.
     * @param value Integer value.
     */
    public void submitBinaryAnnotation(String key, int value) {
        // Zipkin v1 UI and query only support String annotations.
        submitBinaryAnnotation(key, String.valueOf(value));
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

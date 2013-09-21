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
 * Abstract class which implements {@link AnnotationSubmitter} interface. It can be extended by all classes that need to
 * submit annotations to spans.
 * 
 * @author kristof
 * @see ServerTracerImpl
 * @see ClientTracerImpl
 * @see AnnotationSubmitterImpl
 */
abstract class AbstractAnnotationSubmitter implements AnnotationSubmitter {

    private static final String UTF_8 = "UTF-8";

    /**
     * Gets the span to which to add annotations.
     * 
     * @return Span to which to add annotations. Can be <code>null</code>. In that case the different submit methods will not
     *         do anything.
     */
    abstract Span getSpan();

    /**
     * Gets the Endpoint for the annotations.
     * 
     * @return Endpoint for the annotations. Can be <code>null</code>.
     */
    abstract Endpoint getEndPoint();

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName, final long startTime, final long endTime) {
        final Span span = getSpan();
        if (span != null) {
            final Annotation annotation = new Annotation();
            final int duration = (int)(endTime - startTime);
            annotation.setTimestamp(startTime * 1000);
            annotation.setHost(getEndPoint());
            annotation.setDuration(duration * 1000);
            // Duration is currently not supported in the ZipkinUI, so also add it as part of the annotation name.
            annotation.setValue(annotationName + "=" + duration + "ms");
            span.addToAnnotations(annotation);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {
        final Span span = getSpan();
        if (span != null) {
            final Annotation annotation = new Annotation();
            annotation.setTimestamp(currentTimeMicroseconds());
            annotation.setHost(getEndPoint());
            annotation.setValue(annotationName);
            span.addToAnnotations(annotation);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final String value) {
        final Span span = getSpan();
        if (span != null) {
            Validate.notNull(value);
            try {
                final ByteBuffer bb = ByteBuffer.wrap(value.getBytes(UTF_8));
                submitBinaryAnnotation(span, getEndPoint(), key, bb, AnnotationType.STRING);
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final int value) {
        submitBinaryAnnotation(key, String.valueOf(value));

        // This code did not work in the UI, it looks like UI only supports String annotations.
        // final ByteBuffer bb = ByteBuffer.allocate(4);
        // bb.putInt(value);
        // submitBinaryAnnotation(span, endPoint, key, bb, AnnotationType.I32);

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
    private void submitBinaryAnnotation(final Span span, final Endpoint endPoint, final String key, final ByteBuffer value,
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

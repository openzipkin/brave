package com.github.kristofa.brave;

import org.apache.commons.lang.Validate;

import com.twitter.zipkin.gen.Span;

/**
 * {@link AnnotationSubmitter} implementation.
 * 
 * @author kristof
 */
class AnnotationSubmitterImpl implements AnnotationSubmitter {

    private final ServerSpanState state;
    private final CommonAnnotationSubmitter annotationSubmitter;

    AnnotationSubmitterImpl(final ServerSpanState state, final CommonAnnotationSubmitter annotationSubmitter) {
        Validate.notNull(state);
        Validate.notNull(annotationSubmitter);
        this.state = state;
        this.annotationSubmitter = annotationSubmitter;
    }

    @Override
    public void submitAnnotation(final String annotationName, final long startTime, final long endTime) {
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName, startTime, endTime);
        }

    }

    @Override
    public void submitAnnotation(final String annotationName) {
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName);
        }

    }

    @Override
    public void submitBinaryAnnotation(final String key, final String value) {
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
        }

    }

    @Override
    public void submitBinaryAnnotation(final String key, final int value) {
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
        }
    }

}

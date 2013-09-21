package com.github.kristofa.brave;

import org.apache.commons.lang.Validate;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link AnnotationSubmitter} implementation.
 * 
 * @author kristof
 */
class AnnotationSubmitterImpl extends AbstractAnnotationSubmitter {

    private final ServerSpanState state;

    /**
     * Constructor.
     * 
     * @param state ServerSpanState.
     */
    AnnotationSubmitterImpl(final ServerSpanState state) {
        Validate.notNull(state);
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Span getSpan() {
        return state.getCurrentServerSpan().getSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Endpoint getEndPoint() {
        return state.getEndPoint();
    }

}

package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

/**
 * {@link ServerSpanThreadBinder} implementation.
 * 
 * @author kristof
 */
class ServerSpanThreadBinderImpl implements ServerSpanThreadBinder {

    private final ServerSpanState serverSpanState;

    /**
     * Creates a new instance.
     * 
     * @param serverSpanState Server span state, should not be <code>null</code>
     */
    public ServerSpanThreadBinderImpl(final ServerSpanState serverSpanState) {
        Validate.notNull(serverSpanState);
        this.serverSpanState = serverSpanState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentServerSpan() {
        return serverSpanState.getCurrentServerSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentSpan(final Span span) {
        serverSpanState.setCurrentServerSpan(span);
    }

}

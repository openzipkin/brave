package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

/**
 * {@link EndPointSubmitter} implementation.
 * 
 * @author kristof
 */
class EndPointSubmitterImpl implements EndPointSubmitter {

    private final CommonSpanState spanstate;

    /**
     * Creates a new instance.
     * 
     * @param state {@link CommonSpanState}, should not be <code>null</code>.
     */
    EndPointSubmitterImpl(final CommonSpanState state) {
        Validate.notNull(state);
        spanstate = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submit(final String ip, final int port, final String serviceName) {
        spanstate.setEndPoint(new EndPointImpl(ip, port, serviceName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EndPoint getEndPoint() {
        return spanstate.getEndPoint();
    }

}

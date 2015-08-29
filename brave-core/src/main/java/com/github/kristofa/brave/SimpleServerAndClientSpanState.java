package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerAndClientSpanState} implementation that is used with {@link BraveContext}.
 * 
 * @see BraveContext
 */
final class SimpleServerAndClientSpanState implements ServerAndClientSpanState {

    private Endpoint endpoint;
    private Span currentClientSpan;
    private ServerSpan currentServerSpan;
    private String currentClientServiceName;

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        currentServerSpan = span;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Endpoint getServerEndpoint() {
        return endpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan;
    }

    @Override
    public Endpoint getClientEndpoint() {
        if(currentClientServiceName == null){
            return endpoint;
        } else {
            Endpoint newEndpoint = new Endpoint(endpoint);
            newEndpoint.setService_name(currentClientServiceName);
            return newEndpoint;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentClientSpan(final Span span) {
        currentClientSpan = span;
    }

    @Override
    public void setCurrentClientServiceName(String serviceName) {
        currentClientServiceName = serviceName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean sample() {
        return currentServerSpan.getSample();
    }

}

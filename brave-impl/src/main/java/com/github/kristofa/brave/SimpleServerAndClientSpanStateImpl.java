package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerAndClientSpanState} implementation that is used with {@link BraveContext}.
 * 
 * @see BraveContext
 */
class SimpleServerAndClientSpanStateImpl implements ServerAndClientSpanState {

    private Endpoint endPoint;
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
    public Endpoint getServerEndPoint() {
        return endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerEndPoint(final Endpoint endPoint) {
        this.endPoint = endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan;
    }

    @Override
    public Endpoint getClientEndPoint() {
        if(currentClientServiceName == null){
            return endPoint;
        } else {
            Endpoint newEndPoint = new Endpoint(endPoint);
            newEndPoint.setService_name(currentClientServiceName);
            return newEndPoint;
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
    public void incrementServerSpanThreadDuration(final long durationMs) {
        currentServerSpan.incThreadDuration(durationMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getServerSpanThreadDuration() {
        return currentServerSpan.getThreadDuration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean sample() {
        return currentServerSpan.getSample();
    }

}

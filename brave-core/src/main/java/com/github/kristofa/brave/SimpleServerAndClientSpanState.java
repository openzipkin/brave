package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import java.net.InetAddress;

/**
 * {@link ServerAndClientSpanState} implementation that is used with {@link BraveContext}.
 * 
 */
final class SimpleServerAndClientSpanState implements ServerAndClientSpanState {

    private Endpoint endpoint;
    private Span currentClientSpan;
    private ServerSpan currentServerSpan;
    private String currentClientServiceName;

    /**
     * Constructor
     *
     * @param ip InetAddress of current host. If you don't have access to InetAddress you can use InetAddressUtilities#getLocalHostLANAddress()
     * @param port port on which current process is listening.
     * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
     */
    public SimpleServerAndClientSpanState(InetAddress ip, int port, String serviceName) {
        Util.checkNotNull(ip, "ip address must be specified.");
        Util.checkNotBlank(serviceName, "Service name must be specified.");
        endpoint = new Endpoint(InetAddressUtilities.toInt(ip), (short) port, serviceName);
    }

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

    /**
     * {@inheritDoc}
     */
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

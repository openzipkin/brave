package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * {@link ServerAndClientSpanState} implementation that keeps trace state using a ThreadLocal variable.
 * 
 * @author kristof
 */
final class ThreadLocalServerAndClientSpanState implements ServerAndClientSpanState {

    private final static ThreadLocal<ServerSpan> currentServerSpan = new ThreadLocal<ServerSpan>() {

        @Override
        protected ServerSpan initialValue() {
            return ServerSpan.create(null);
        }
    };
    private final static ThreadLocal<Span> currentClientSpan = new ThreadLocal<>();

    private final static ThreadLocal<String> currentClientServiceName = new ThreadLocal<>();

    private final Endpoint endpoint;

    /**
     * Constructor
     *
     * @param ip InetAddress of current host. If you don't have access to InetAddress you can use InetAddressUtilities#getLocalHostLANAddress()
     * @param port port on which current process is listening.
     * @param serviceName Service name. Only relevant if we do server side tracing.
     */
    public ThreadLocalServerAndClientSpanState(InetAddress ip, int port, String serviceName) {
        Util.checkNotNull(ip, "ip address must be specified.");
        Util.checkNotBlank(serviceName, "Service name must be specified.");
        endpoint = new Endpoint(InetAddressUtilities.toInt(ip), (short) port, serviceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan.get();
    }

    @Override
    public Endpoint getServerEndpoint() {
        return endpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        if (span == null) {
            currentServerSpan.remove();
        } else {
            currentServerSpan.set(span);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Endpoint getClientEndpoint() {
        final String serviceName = currentClientServiceName.get();
        if (serviceName == null) {
            return endpoint;
        } else {
            final Endpoint ep = new Endpoint(endpoint);
            ep.setService_name(serviceName);
            return ep;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentClientSpan(final Span span) {

        currentClientSpan.set(span);
    }

    @Override
    public void setCurrentClientServiceName(final String serviceName) {
        currentClientServiceName.set(serviceName);
    }

    @Override
    public Boolean sample() {
        return currentServerSpan.get().getSample();
    }

}

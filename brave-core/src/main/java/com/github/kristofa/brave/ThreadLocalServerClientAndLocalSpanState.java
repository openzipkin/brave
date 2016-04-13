package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import java.net.InetAddress;

/**
 * {@link ServerClientAndLocalSpanState} implementation that keeps trace state using a ThreadLocal variable.
 * 
 * @author kristof
 */
public final class ThreadLocalServerClientAndLocalSpanState implements ServerClientAndLocalSpanState {

    private final static ThreadLocal<ServerSpan> currentServerSpan = new ThreadLocal<ServerSpan>() {

        @Override
        protected ServerSpan initialValue() {
            return ServerSpan.create(null);
        }
    };
    private final static ThreadLocal<Span> currentClientSpan = new ThreadLocal<Span>();

    private final static ThreadLocal<Span> currentLocalSpan = new ThreadLocal<Span>();

    private final Endpoint endpoint;

    /**
     * Constructor
     *
     * @param ip InetAddress of current host. If you don't have access to InetAddress you can use InetAddressUtilities#getLocalHostLANAddress()
     * @param port port on which current process is listening.
     * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
     * @deprecated Please switch to constructor that takes 'int' for ip. This only does a conversion from the InetAddress to integer anyway
     *             and using InetAddress can result in ns lookup and nasty side effects.
     */
    @Deprecated
    public ThreadLocalServerClientAndLocalSpanState(InetAddress ip, int port, String serviceName) {
        Util.checkNotNull(ip, "ip address must be specified.");
        Util.checkNotBlank(serviceName, "Service name must be specified.");
        endpoint = Endpoint.create(serviceName, InetAddressUtilities.toInt(ip), port);
    }

    /**
     * Constructor
     *
     * @param ip Int representation of ipv4 address.
     * @param port port on which current process is listening.
     * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
     */
    public ThreadLocalServerClientAndLocalSpanState(int ip, int port, String serviceName) {
        Util.checkNotBlank(serviceName, "Service name must be specified.");
        endpoint = Endpoint.create(serviceName, ip, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan.get();
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
    public Endpoint endpoint() {
        return endpoint;
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
    public Boolean sample() {
        return currentServerSpan.get().getSample();
    }

    @Override
    public Span getCurrentLocalSpan() {
        return currentLocalSpan.get();
    }

    @Override
    public void setCurrentLocalSpan(Span span) {
        if (span == null) {
            currentLocalSpan.remove();
        } else {
            currentLocalSpan.set(span);
        }
    }
}

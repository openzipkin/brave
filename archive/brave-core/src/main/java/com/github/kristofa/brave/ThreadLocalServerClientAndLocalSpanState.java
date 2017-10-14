package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.net.InetAddress;
import javax.annotation.Nonnull;

/**
 * {@link ServerClientAndLocalSpanState} implementation that keeps trace state using a ThreadLocal variable.
 * 
 * @author kristof
 */
public final class ThreadLocalServerClientAndLocalSpanState implements ServerClientAndLocalSpanState {

    private final static ThreadLocal<ServerSpan> currentServerSpan = new ThreadLocal<ServerSpan>() {

        @Override
        protected ServerSpan initialValue() {
            return ServerSpan.EMPTY;
        }
    };
    private final static ThreadLocal<Span> currentClientSpan = new ThreadLocal<Span>();

    private final static ThreadLocal<Span> currentLocalSpan = new ThreadLocal<Span>();

    private final Endpoint endpoint;

    // visible for testing
    public static void clear() {
        currentServerSpan.remove();
        currentClientSpan.remove();
        currentLocalSpan.remove();
    }

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
        this(InetAddressUtilities.toInt(Util.checkNotNull(ip, "ip address must be specified.")), port, serviceName);
    }

    /**
     * Constructor
     *
     * @param ip Int representation of ipv4 address.
     * @param port port on which current process is listening.
     * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
     */
    public ThreadLocalServerClientAndLocalSpanState(int ip, int port, String serviceName) {
        this(Endpoint.builder().ipv4(ip).port(port).serviceName(serviceName).build());
    }

    /**
     * @param endpoint Endpoint of the local service being traced.
     */
    public ThreadLocalServerClientAndLocalSpanState(Endpoint endpoint) {
        Util.checkNotNull(endpoint, "endpoint must be specified.");
        Util.checkNotBlank(endpoint.service_name, "Service name must be specified.");
        this.endpoint = endpoint;
    }

    /** Never returns null: {@code setCurrentServerSpan(null)} coerces to {@link ServerSpan#EMPTY} */
    @Override
    @Nonnull
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentServerSpan(@Nullable ServerSpan span) {
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
    @Nullable
    public Span getCurrentClientSpan() {
        return currentClientSpan.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentClientSpan(@Nullable Span span) {
        currentClientSpan.set(span);
    }

    @Override
    @Nullable
    public Boolean sample() {
        return currentServerSpan.get().getSample();
    }

    @Override
    @Nullable
    public Span getCurrentLocalSpan() {
        return currentLocalSpan.get();
    }

    @Override
    public void setCurrentLocalSpan(@Nullable Span span) {
        if (span == null) {
            currentLocalSpan.remove();
        } else {
            currentLocalSpan.set(span);
        }
    }
}

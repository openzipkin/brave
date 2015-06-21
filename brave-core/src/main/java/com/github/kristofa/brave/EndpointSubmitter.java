package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Each {@link Annotation} we submit as part of a {@link Span} has an {@link Endpoint} defined. The {@link Endpoint} needs to
 * be set once for a service.
 * <p>
 * This is the interface that allows to initialize the endpoint for your service.
 * 
 * @author kristof
 */
public class EndpointSubmitter {

    private final ServerAndClientSpanState state;

    /**
     * Creates a new instance.
     *
     * @param state {@link CommonSpanState}, should not be <code>null</code>.
     */
    public EndpointSubmitter(ServerAndClientSpanState state) {
        this.state = checkNotNull(state, "state");
    }

    /**
     * Sets end point.
     *
     * @param ip Ip address, example 10.0.1.5
     * @param port Port.
     * @param serviceName Service name.
     */
    public void submit(final String ip, final int port, final String serviceName) {

        final int ipv4 = ipAddressToInt(ip);
        final Endpoint endpoint = new Endpoint(ipv4, (short)port, serviceName);

        state.setServerEndpoint(endpoint);
    }


    /**
     * Indicates if Endpoint has already been set.
     *
     * @return <code>true</code> in case end point has already been submitted, <code>false</code> in case it has not been
     *         submitted yet.
     */
    public boolean endpointSubmitted() {
        return state.getServerEndpoint() != null;
    }

    private static int ipAddressToInt(final String ip) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(ip);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
    }
}

package com.github.kristofa.brave;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Endpoint;

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

        final int ipv4 = ipAddressToInt(ip);
        final Endpoint endpoint = new Endpoint(ipv4, (short)port, serviceName);

        spanstate.setEndPoint(endpoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endPointSubmitted() {
        return spanstate.getEndPoint() != null;
    }

    private int ipAddressToInt(final String ip) {
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(ip);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
    }

}

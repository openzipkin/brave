package com.github.kristofa.brave.zipkin;

import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author botizac
 */
public class SocketTransportProviderTest {

    @Test
    public void testGetTransport() {
        SocketTransportProvider transportProvider = new SocketTransportProvider("collector.zipkin.com", 9160);

        TTransport transport = transportProvider.getTransport();

        assertTrue(transport instanceof TSocket);

        //not much we can test without connecting...
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongHost() {
        new SocketTransportProvider("", 1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongPort() {
        new SocketTransportProvider("collector.zipkin.com", -1);
    }
}

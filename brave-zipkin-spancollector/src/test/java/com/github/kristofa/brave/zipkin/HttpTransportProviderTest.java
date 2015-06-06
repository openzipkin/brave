package com.github.kristofa.brave.zipkin;

import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author botizac
 */
public class HttpTransportProviderTest {

    @Test(expected = IllegalArgumentException.class)
    public void wrongUrl() {
        new HttpTransportProvider("http8080");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingUrl() {
        new HttpTransportProvider(" ");
    }

    @Test
    public void testGetTransport() {
        HttpTransportProvider transportProvider = new HttpTransportProvider("http://collector.zipkin.com", 3000, 3000);

        TTransport transport = transportProvider.getTransport();

        assertTrue(transport instanceof THttpClient);

    }
}

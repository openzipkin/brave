package com.github.kristofa.brave;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertNotNull;

public class InetAddressResolverTest {

    @Test
    public void testResolvingInetAddressDoesNotThrowException() throws UnknownHostException {
       InetAddress address = InetAddressUtilities.getLocalHostLANAddress();
       assertNotNull(address.getHostAddress());
    }
}

package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class EndPointImplTest {

    private final static int IP_ADDRESS = 513;
    private final static short PORT = 8080;
    private final static short OTHER_PORT = 8081;
    private final static String SERVICE_NAME = "service1";

    private EndPointImpl endpoint;

    @Before
    public void setup() {
        endpoint = new EndPointImpl(IP_ADDRESS, PORT, SERVICE_NAME);
    }

    @Test
    public void testHashCode() {
        final EndPointImpl equalEndPoint = new EndPointImpl(IP_ADDRESS, PORT, SERVICE_NAME);
        assertEquals("Equal objects should have same hash code.", endpoint.hashCode(), equalEndPoint.hashCode());
    }

    @Test(expected = NullPointerException.class)
    public void testEndPointImplNullServiceName() {
        new EndPointImpl(10, (short)8080, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndPointImplEmptyServiceName() {
        new EndPointImpl(10, (short)8080, "");
    }

    @Test
    public void testGetIpAddress() {
        assertEquals(IP_ADDRESS, endpoint.getIpAddress());
    }

    @Test
    public void testGetPort() {
        assertEquals(PORT, endpoint.getPort());
    }

    @Test
    public void testGetServiceName() {
        assertEquals(SERVICE_NAME, endpoint.getServiceName());
    }

    @Test
    public void testEqualsObject() {
        assertTrue(endpoint.equals(endpoint));
        assertFalse(endpoint.equals(null));
        assertFalse(endpoint.equals(new String()));

        final EndPointImpl equalEndPoint = new EndPointImpl(IP_ADDRESS, PORT, SERVICE_NAME);
        assertTrue(endpoint.equals(equalEndPoint));
        final EndPointImpl nonEqualEndPoint1 = new EndPointImpl(IP_ADDRESS + 1, PORT, SERVICE_NAME);
        assertFalse(endpoint.equals(nonEqualEndPoint1));
        final EndPointImpl nonEqualEndPoint2 = new EndPointImpl(IP_ADDRESS, OTHER_PORT, SERVICE_NAME);
        assertFalse(endpoint.equals(nonEqualEndPoint2));
        final EndPointImpl nonEqualEndPoint3 = new EndPointImpl(IP_ADDRESS + 1, PORT, SERVICE_NAME + "b");
        assertFalse(endpoint.equals(nonEqualEndPoint3));

    }

    @Test
    public void testToString() {
        assertEquals("[ip: " + IP_ADDRESS + ", port: " + PORT + ", service name: " + SERVICE_NAME + "]", endpoint.toString());
    }

}

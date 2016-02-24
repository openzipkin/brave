package com.twitter.zipkin.gen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EndpointTest {

  @Test
  public void testServiceNameLowercase() {
    Endpoint ep = Endpoint.create("ServiceName", 1, 1);
    assertEquals("servicename", ep.service_name);
  }
}

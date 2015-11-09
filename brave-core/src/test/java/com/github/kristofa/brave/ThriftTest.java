package com.github.kristofa.brave;


import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Zipkin's thrift specifies that span and service name are lowercase. This makes sure that folks
 * modify generated thrifts to enforce that.
 */
public class ThriftTest {

  @Test
  public void testSpanNameLowercase() {
    assertEquals("spanname", new Span(1, "SpanName", 1, null, null).getName());
    assertEquals("spanname", new Span().setName("SpanName").getName());
  }

  @Test
  public void testEndpointServiceNameLowercase() {
    assertEquals("servicename", new Endpoint(1, (short) 1, "ServiceName").getService_name());
    assertEquals("servicename", new Endpoint().setService_name("ServiceName").getService_name());
  }
}

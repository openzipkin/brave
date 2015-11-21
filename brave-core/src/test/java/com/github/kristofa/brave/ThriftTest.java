package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * This enforces the thrifts are modified to enforce certain behavior or use cases.
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

  @Test
  public void canStoreNanoTimeForDurationCalculation() {
    Span span = new Span();
    span.startTick = System.nanoTime();
  }
}

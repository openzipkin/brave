package com.github.kristofa.brave;

import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.github.kristofa.brave.internal.Util.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * This enforces the thrifts are modified to enforce certain behavior or use cases.
 */
public class ThriftTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testBinaryAnnotationCtorForString() {
    BinaryAnnotation ba = new BinaryAnnotation("key", "value");
    assertEquals("key", ba.getKey());
    assertEquals("value", new String(ba.getValue(), UTF_8));
    assertEquals(AnnotationType.STRING, ba.getAnnotation_type());
  }

  @Test
  public void testBinaryAnnotationCtorForString_noBlankKeys() {
    thrown.expect(IllegalArgumentException.class);
    new BinaryAnnotation("", "value");
  }

  @Test
  public void testBinaryAnnotationCtorForString_noNullValues() {
    thrown.expect(NullPointerException.class);
    new BinaryAnnotation("key", null);
  }

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

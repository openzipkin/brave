package com.twitter.zipkin.gen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpanTest {
  @Test
  public void testNameLowercase() {
    assertEquals("spanname", new Span().setName("SpanName").getName());
  }

  @Test
  public void canStoreNanoTimeForDurationCalculation() {
    Span span = new Span();
    span.startTick = System.nanoTime();
  }
}

package com.twitter.zipkin.gen;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.github.kristofa.brave.internal.Util.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * This enforces the thrifts are modified to enforce certain behavior or use cases.
 */
public class BinaryAnnotationTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCtorForString() {
    BinaryAnnotation ba = BinaryAnnotation.create("key", "value", null);
    assertEquals("key", ba.getKey());
    assertEquals("value", new String(ba.getValue(), UTF_8));
    assertEquals(AnnotationType.STRING, ba.type);
  }

  @Test
  public void testCtorForString_noBlankKeys() {
    thrown.expect(IllegalArgumentException.class);
    BinaryAnnotation.create("", "value", null);
  }

  @Test
  public void testCtorForString_noNullValues() {
    thrown.expect(NullPointerException.class);
    BinaryAnnotation.create("key", null, null);
  }
}

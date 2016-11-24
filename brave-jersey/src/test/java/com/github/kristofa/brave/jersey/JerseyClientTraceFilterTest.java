package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A test for {@link JerseyClientTraceFilter}.
 *
 * @author pabstec on 11/11/16.
 */
public class JerseyClientTraceFilterTest {
  @Test
  public void testConstructor_Brave() {
    JerseyClientTraceFilter jerseyClientTraceFilter = new JerseyClientTraceFilter(new Brave.Builder().build());
    assertNotNull(jerseyClientTraceFilter);
  }
}

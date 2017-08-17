package com.github.kristofa.brave.internal;

import brave.Tracing;
import com.github.kristofa.brave.TracerAdapter;
import org.junit.After;

public class Brave4MaybeAddClientAddressTest extends MaybeAddClientAddressTest {
  public Brave4MaybeAddClientAddressTest() {
    brave = TracerAdapter.newBrave(Tracing.newBuilder().reporter(spans::add).build().tracer());
  }

  @After public void close(){
    Tracing.current().close();
  }
}

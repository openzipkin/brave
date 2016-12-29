package com.github.kristofa.brave.internal;

import brave.Tracer;
import com.github.kristofa.brave.TracerAdapter;

public class Brave4MaybeAddClientAddressTest extends MaybeAddClientAddressTest {
  public Brave4MaybeAddClientAddressTest() {
    brave = TracerAdapter.newBrave(Tracer.newBuilder().reporter(spans::add).build());
  }
}

package com.github.kristofa.brave;

import brave.Tracing;

public class Brave4ServerTracerTest extends ServerTracerTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .clock(clock::currentTimeMicroseconds)
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build().tracer());
  }
}

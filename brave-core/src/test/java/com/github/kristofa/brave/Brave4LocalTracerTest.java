package com.github.kristofa.brave;

import brave.Tracer;

public class Brave4LocalTracerTest extends LocalTracerTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracer.newBuilder()
        .clock(clock::currentTimeMicroseconds)
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build());
  }

  @Override Brave newBrave(ServerClientAndLocalSpanState state) {
    return TracerAdapter.newBrave(Tracer.newBuilder()
        .clock(clock::currentTimeMicroseconds)
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build(), state);
  }
}
